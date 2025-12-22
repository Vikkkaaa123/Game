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

;; ========== TELNET HANDLER ==========
(defn- handle-telnet-client [in out]
  (binding [*in* (io/reader in)
            *out* (io/writer out)]
    (try
      (print "\nВведите ваше имя: ") (flush)
      (let [player-name (read-line)]
        (when (and player-name (not (str/blank? player-name)))
          (let [result (api/register-player player-name nil)]
            (if (:error result)
              (println (:message result))
              (do
                (println (str "Добро пожаловать, " player-name "!"))
                (println "Введите 'help' для списка команд.")
                
                (try
                  (loop [input (read-line)]
                    (when input
                      (let [response (api/handle-web-command player-name input)]
                        (println (:message response))
                        (print "> ") (flush)
                        (recur (read-line)))))
                  (catch Exception _ 
                    (api/logout-player player-name))))))))
      (catch Exception e
        (println "Ошибка:" (.getMessage e))))))

;; ========== WEBSOCKET HANDLER ==========
(defn websocket-handler [request]
  (http-kit/with-channel request channel
    ;; Приветствие при подключении
    (http-kit/send! channel 
      (json/generate-string 
        {:type "welcome"
         :message "Добро пожаловать в игру 'Побег из лаборатории'!"}))
    
    (http-kit/on-receive channel (fn [data]
      (try
        (let [msg (json/parse-string data true)
              msg-type (:type msg)]
          
          (cond
            ;; РЕГИСТРАЦИЯ
            (= msg-type "register")
            (let [player-name (:content msg)
                  result (api/register-player player-name channel)
                  game-state (api/get-game-state player-name)]  ;; ← ПОЛУЧАЕМ СОСТОЯНИЕ
              
              (http-kit/send! channel 
                (json/generate-string 
                  (if (:error result)
                    {:type "error" :message (:message result)}
                    {:type "registered" 
                    :player player-name
                    :message (:message result)
                    :game-state game-state}))))  ;; ← ДОБАВЛЯЕМ В ОТВЕТ
            
            ;; КОМАНДА
            (= msg-type "command")
            (let [player-name (:player msg)
                  command (:content msg)
                  result (api/handle-web-command player-name command)
                  game-state (api/get-game-state player-name)]
              
              ;; ОТПРАВЛЯЕМ ОТВЕТ С ИГРОВЫМ СОСТОЯНИЕМ
              (http-kit/send! channel 
                (json/generate-string 
                  (if (:error result)
                    {:type "error" :message (:message result)}
                    {:type "command-response"
                     :result result
                     :game-state game-state}))))
            
            ;; НЕИЗВЕСТНЫЙ ТИП
            :else
            (http-kit/send! channel 
              (json/generate-string 
                {:type "error" 
                 :message "Неизвестный тип сообщения"}))))
        
        (catch Exception e
          (http-kit/send! channel 
            (json/generate-string 
              {:type "error" 
               :message (str "Ошибка сервера: " (.getMessage e))}))))))))

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
              :players (count (world/get-all-players))})})
  
  (route/resources "/")
  
  (route/not-found 
    {:status 404
     :body (json/generate-string {:error "Не найдено"})}))

;; Middleware
(def app
  (-> app-routes))

;; ========== ЗАПУСК СЕРВЕРОВ ==========
(defn start-servers [telnet-port web-port]
  (println "Запуск единого сервера...")
  (println "Telnet порт:" telnet-port)
  (println "Web порт:" web-port)
  
  ;; Инициализация игры
  (api/init-game)
  
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