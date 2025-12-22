(ns game.server.websocket
  (:require [org.httpkit.server :as http-kit]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.json :refer [wrap-json-response]]
            [cheshire.core :as json]
            [game.api :as api]
            [clojure.tools.logging :as log])
  (:import [java.util UUID]))

;; WebSocket соединения
(defonce connections (atom {}))

;; WebSocket обработчик
(defn websocket-handler [request]
  (http-kit/with-channel request channel
    (let [session-id (str (UUID/randomUUID))]
      (log/info "Новое WebSocket соединение:" session-id)
      
      ;; Сохраняем соединение
      (swap! connections assoc session-id {:id session-id :channel channel})
      
      ;; Отправляем приветствие
      (http-kit/send! channel 
        (json/generate-string 
          {:type "welcome"
           :message "Добро пожаловать в игру 'Побег из лаборатории'!"
           :session-id session-id}))
      
      ;; Обработка входящих сообщений
      (http-kit/on-receive channel (fn [data]
        (try
          (let [message (json/parse-string data true)
                msg-type (:type message)
                player-name (:player message)
                content (:content message)]
            
            (case msg-type
              ;; Регистрация игрока
              "register"
              (let [result (api/register-player content channel)]
                (http-kit/send! channel (json/generate-string 
                  {:type "registered"
                   :player content
                   :game-state (api/get-game-state content)})))
              
              ;; Игровая команда
              "command"
              (let [result (api/handle-web-command player-name content)
                    game-state (api/get-game-state player-name)]
                (http-kit/send! channel (json/generate-string 
                  {:type "command-response"
                   :result result
                   :game-state game-state})))
              
              ;; Получение состояния
              "get-state"
              (let [game-state (api/get-game-state player-name)]
                (http-kit/send! channel (json/generate-string 
                  {:type "game-state"
                   :game-state game-state})))
              
              ;; Чат
              "chat"
              (let [result (api/broadcast-to-room player-name content)]
                (http-kit/send! channel (json/generate-string 
                  {:type "chat-sent"
                   :result result})))
              
              ;; Выход
              "logout"
              (let [result (api/logout-player player-name)]
                (swap! connections dissoc session-id)
                (http-kit/send! channel (json/generate-string 
                  {:type "logged-out"
                   :result result})))
              
              ;; Неизвестный тип
              (http-kit/send! channel (json/generate-string 
                {:type "error"
                 :message "Неизвестный тип сообщения"}))))
          
          (catch Exception e
            (log/error "Ошибка обработки сообщения:" e)
            (http-kit/send! channel (json/generate-string 
              {:type "error"
               :message (str "Ошибка сервера: " (.getMessage e))}))))))
      
      ;; При закрытии соединения
      (http-kit/on-close channel (fn [status]
        (log/info "Соединение закрыто:" session-id)
        (swap! connections dissoc session-id)
        ;; Если был зарегистрирован игрок - выходим
        (doseq [[id conn] @connections]
          (when (= channel (:channel conn))
            (api/logout-player (:player conn)))))))))

;; HTTP маршруты
(defroutes app-routes
  (GET "/" [] 
    (slurp (clojure.java.io/resource "public/index.html")))
  
  (GET "/ws" [] websocket-handler)
  
  (GET "/status" [] 
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (json/generate-string 
             {:status "ok"
              :connections (count @connections)
              :timestamp (System/currentTimeMillis)})})
  
  (GET "/api/init" []
    {:status 200
     :body (json/generate-string (api/init-game))})
  
  (route/resources "/")
  
  (route/not-found 
    {:status 404
     :headers {"Content-Type" "application/json"}
     :body (json/generate-string 
             {:error "Страница не найдена"})}))

;; Middleware
(def app
  (-> app-routes
      (wrap-json-response)
      (wrap-defaults (assoc site-defaults :security false))))

;; Запуск сервера
(defn start-server [port]
  (println "🚀 Запуск веб-сервера на порту" port)
  (println "🌐 Откройте в браузере: http://localhost:" port)
  (println "🔄 Инициализация игры...")
  (api/init-game)
  (http-kit/run-server #'app {:port port}))

(defn -main [& [port]]
  (let [port (or (when port (Integer/parseInt port)) 8080)]
    (start-server port)))