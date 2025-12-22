(ns game.server.unified
  (:require [org.httpkit.server :as http-kit]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [cheshire.core :as json]
            [server.socket :as socket]
            [game.world :as world]
            [game.api :as api]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; ========== ХРАНИЛИЩЕ СОЕДИНЕНИЙ ==========
(defonce connections (atom {})) ; {player-name {:type :websocket/:telnet, :channel/:out}}

;; ========== УТИЛИТЫ ДЛЯ ОТПРАВКИ СООБЩЕНИЙ ==========
(defn send-to-websocket [channel message]
  (when (and channel (not (.isClosed channel)))
    (http-kit/send! channel (json/generate-string message))))

(defn send-to-telnet [out message]
  (when out
    (try
      (let [writer (io/writer out)]
        ;; Преобразуем сообщение в читаемый формат
        (cond
          ;; Если это сообщение чата - преобразуем JSON в текст
          (map? message) 
          (let [msg-type (:type message)]
            (case msg-type
              "chat"
              (let [from (:from message)
                    msg-text (:message message)
                    timestamp (:timestamp message)
                    time-str (when timestamp 
                               (.format (java.text.SimpleDateFormat. "HH:mm:ss") 
                                        (java.util.Date. timestamp)))]
                (.write writer (str (when time-str (str "[" time-str "] ")) 
                                   from ": " msg-text "\n"))
                (.write writer "> ")
                (.flush writer))
              
              ;; Другие типы сообщений
              "error"
              (do
                (.write writer (str "Ошибка: " (:message message) "\n"))
                (.write writer "> ")
                (.flush writer))
              
              ;; По умолчанию - JSON
              (do
                (.write writer (str (json/generate-string message) "\n"))
                (.write writer "> ")
                (.flush writer))))
          
          ;; Если это строка - отправляем как есть
          (string? message)
          (do
            (.write writer (str message "\n"))
            (.write writer "> ")
            (.flush writer))
          
          ;; Любой другой тип - преобразуем в строку
          :else
          (do
            (.write writer (str (pr-str message) "\n"))
            (.write writer "> ")
            (.flush writer)))
        true)
      
      (catch Exception e
        (println "Ошибка отправки в telnet:" (.getMessage e))
        false))))

(defn send-to-player [player-name message]
  (when-let [conn (@connections player-name)]
    (case (:type conn)
      :websocket (send-to-websocket (:channel conn) message)
      :telnet (send-to-telnet (:out conn) message))))

;; ========== РАССЫЛКА СООБЩЕНИЙ В КОМНАТЕ ==========
(defn broadcast-to-room [from-player message]
  (try
    (let [room-key (world/get-player-room from-player)
          players-in-room (world/get-room-players room-key)
          other-players (disj players-in-room from-player)]
      
      ;; Отправляем сообщение всем, кроме отправителя
      (doseq [player other-players]
        (send-to-player player
          {:type "chat"
           :from from-player
           :message message
           :timestamp (System/currentTimeMillis)}))
      
      ;; Возвращаем результат для отправителя
      {:success true
       :message (str "Вы сказали: \"" message "\"")
       :recipients-count (count other-players)})
    
    (catch Exception e
      {:error true :message (str "Ошибка чата: " (.getMessage e))})))

;; ========== TELNET HANDLER ==========
(defn- handle-telnet-client [in out]
  (let [player-name (atom nil)]
    (try
      (let [reader (io/reader in)
            writer (io/writer out)]
        
        (.write writer "Введите ваше имя: ")
        (.flush writer)
        
        (let [name-input (.readLine reader)]
          (when (and name-input (not (str/blank? name-input)))
            (reset! player-name name-input)
            
            ;; Сохраняем соединение
            (swap! connections assoc name-input {:type :telnet :out out})
            
            (let [result (api/register-player name-input nil)]
              (if (:error result)
                (do
                  (.write writer (str (:message result) "\n"))
                  (.flush writer))
                (do
                  (.write writer (str "Добро пожаловать, " name-input "!\n"))
                  (.write writer "Введите 'help' для списка команд.\n")
                  (.flush writer)
                  
                  (try
                    (loop [input (.readLine reader)]
                      (when input
                        (let [trimmed-input (str/trim input)]
                          ;; Проверяем, это команда say?
                          (if (or (str/starts-with? trimmed-input "сказать ")
                                 (str/starts-with? trimmed-input "say ")
                                 (str/starts-with? trimmed-input "с "))
                            ;; Обработка чата
                            (let [message (str/join " " (rest (str/split trimmed-input #"\s+")))
                                  result (broadcast-to-room name-input message)]
                              (.write writer (str (:message result) "\n> "))
                              (.flush writer))
                            ;; Обычная команда
                            (let [response (api/handle-web-command name-input input)]
                              (.write writer (str (:message response) "\n> "))
                              (.flush writer))))
                        
                        (recur (.readLine reader))))
                    
                    (catch Exception _
                      (api/logout-player name-input)
                      (swap! connections dissoc name-input)))))))))
      
      (catch Exception e
        (println "Ошибка telnet:" (.getMessage e)))
      
      (finally
        ;; Убираем из соединений при отключении
        (when @player-name
          (api/logout-player @player-name)
          (swap! connections dissoc @player-name))))))

;; ========== WEBSOCKET HANDLER ==========
(defn websocket-handler [request]
  (http-kit/with-channel request channel
    ;; Приветствие при подключении
    (send-to-websocket channel
      {:type "welcome"
       :message "Добро пожаловать в игру 'Побег из лаборатории'!"})
    
    (http-kit/on-receive channel (fn [data]
      (try
        (let [msg (json/parse-string data true)
              msg-type (:type msg)]
          
          (case msg-type
            ;; РЕГИСТРАЦИЯ
            "register"
            (let [player-name (:content msg)
                  result (api/register-player player-name channel)
                  game-state (api/get-game-state player-name)]
              
              ;; Сохраняем соединение
              (when (not (:error result))
                (swap! connections assoc player-name {:type :websocket :channel channel}))
              
              (send-to-websocket channel
                (if (:error result)
                  {:type "error" :message (:message result)}
                  {:type "registered" 
                   :player player-name
                   :message (:message result)
                   :game-state game-state})))
            
            ;; КОМАНДА
            "command"
            (let [player-name (:player msg)
                  command (:content msg)]
              
              (send-to-websocket channel
                (let [result (api/handle-web-command player-name command)
                      game-state (api/get-game-state player-name)]
                  {:type "command-response"
                   :result result
                   :game-state game-state})))
            
            ;; ЧАТ
            "chat"
            (let [player-name (:player msg)
                  message (:content msg)
                  result (broadcast-to-room player-name message)]
              
              ;; Отправляем результат отправителю
              (send-to-websocket channel
                {:type "command-response"
                 :result result}))
            
            ;; НЕИЗВЕСТНЫЙ ТИП
            (send-to-websocket channel
              {:type "error" 
               :message "Неизвестный тип сообщения"})))
        
        (catch Exception e
          (send-to-websocket channel
            {:type "error" 
             :message (str "Ошибка сервера: " (.getMessage e))})))))
    
    ;; При закрытии соединения
    (http-kit/on-close channel (fn [status]
      (doseq [[name conn] @connections]
        (when (= (:channel conn) channel)
          (swap! connections dissoc name)
          (api/logout-player name)))))))

;; ========== HTTP ROUTES ==========
(defroutes app-routes
  (GET "/" [] 
    (try
      (slurp (clojure.java.io/resource "public/index.html"))
      (catch Exception _
        "<h1>Игра 'Побег из лаборатории'</h1><p>Откройте консоль разработчика (F12)</p>")))
  
  (GET "/ws" [] websocket-handler)
  
  (GET "/status" [] 
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (json/generate-string 
             {:status "ok"
              :players (count @connections)
              :connections (count @connections)})})
  
  (route/resources "/")
  
  (route/not-found 
    {:status 404
     :body (json/generate-string {:error "Не найдено"})}))

(def app app-routes)

;; ========== ЗАПУСК СЕРВЕРОВ ==========
(defn start-servers [telnet-port web-port]
  (println "Запуск единого сервера с поддержкой чата...")
  (println "Telnet порт:" telnet-port)
  (println "Web порт:" web-port)
  
  (api/init-game)
  (reset! connections {})
  
  ;; Telnet сервер
  (future
    (try
      (socket/create-server (Integer. telnet-port) handle-telnet-client)
      (println "Telnet сервер запущен")
      (catch Exception e
        (println "Ошибка telnet:" (.getMessage e)))))
  
  ;; Веб-сервер
  (Thread/sleep 300)
  (let [server (http-kit/run-server #'app {:port web-port})]
    (println "Веб-сервер запущен: http://localhost:" web-port)
    server))

(defn -main [& args]
  (let [telnet-port (or (when args (first args)) 3333)
        web-port (or (when args (second args)) 8080)]
    (start-servers telnet-port web-port)))