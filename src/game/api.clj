(ns game.api
  "API слой для веб-интерфейса. Использует game.world напрямую"
  (:require [game.core :as game]
            [game.world :as world]
            [clojure.string :as str]))

;; Хранилище WebSocket каналов
(defonce websocket-channels (atom {}))

;; 1. РЕГИСТРАЦИЯ ИГРОКА
(defn register-player [player-name channel]
  (try
    (if (world/player-exists? player-name)
      {:error true :message (str "Игрок " player-name " уже существует")}
      (do
        ;; Добавляем в мир
        (world/add-player! player-name)
        ;; Сохраняем WebSocket канал
        (swap! websocket-channels assoc player-name channel)
        {:status "ok" 
         :message (str "Игрок " player-name " зарегистрирован")
         :player player-name
         :room (world/get-player-room player-name)}))
    (catch Exception e
      {:error true :message (str "Ошибка регистрации: " (.getMessage e))})))

;; 2. ОБРАБОТКА КОМАНД - ПРОКСИ К game.core
(defn handle-web-command [player-name input]
  (try
    (if-not (world/player-exists? player-name)
      {:error true :message "Игрок не найден. Переподключитесь."}
      
      ;; Вызываем основную логику игры
      (let [response (game/handle-command player-name input)]
        
        ;; Если команда "say" - обрабатываем чат
        (if (and (map? response) (= (:type response) :chat-message))
          ;; Это сообщение чата
          (do
            ;; Рассылаем сообщение всем в комнате
            (let [room-players (world/get-room-players 
                                 (world/get-player-room player-name))
                  other-players (disj room-players player-name)]
              
              (doseq [other-player other-players]
                (when-let [chan (@websocket-channels other-player)]
                  ;; Отправляем сообщение другим игрокам
                  nil ;; TODO: реализовать отправку
                ))
              
              {:status "ok"
               :message (str "Вы сказали: \"" (:message response) "\"")
               :broadcast-count (count other-players)}))
          
          ;; Обычная команда
          {:status "ok"
           :message (str response)})))  ; response уже строка из game.core
    
    (catch Exception e
      {:error true 
       :message (str "Ошибка выполнения команды: " (.getMessage e))})))

;; 3. ПОЛУЧЕНИЕ СОСТОЯНИЯ ИГРЫ
(defn get-game-state [player-name]
  (try
    (if-not (world/player-exists? player-name)
      {:error true :message "Игрок не найден"}
      (let [room-key (world/get-player-room player-name)
            room-data (world/get-room room-key)]
        {:status "ok"
         :player {:name player-name
                  :room room-key
                  :inventory (world/get-player-inventory player-name)}
         :room {:name (:name room-data)
                :desc (:desc room-data)
                :items (world/get-room-items room-key)
                :players (world/get-room-players room-key)}
         :timestamp (System/currentTimeMillis)}))
    (catch Exception e
      {:error true :message (str "Ошибка: " (.getMessage e))})))

;; 4. ВЫХОД ИГРОКА
(defn logout-player [player-name]
  (try
    (world/remove-player! player-name)
    (swap! websocket-channels dissoc player-name)
    {:status "ok" :message "Игрок вышел"}
    (catch Exception e
      {:error true :message (str "Ошибка: " (.getMessage e))})))

;; 5. РАССЫЛКА СООБЩЕНИЙ В КОМНАТЕ
(defn broadcast-to-room [player-name message]
  (try
    (let [room-key (world/get-player-room player-name)
          other-players (disj (world/get-room-players room-key) player-name)]
      
      (doseq [other-player other-players]
        (when-let [chan (@websocket-channels other-player)]
          ;; TODO: отправить через WebSocket
          (println "Сообщение для" other-player ":" message)))
      
      {:status "ok" 
       :message (str "Сообщение отправлено " (count other-players) " игрокам")})
    (catch Exception e
      {:error true :message (str "Ошибка: " (.getMessage e))})))

;; 6. ИНИЦИАЛИЗАЦИЯ
(defn init-game []
  (println "[API] Инициализация через game.world...")
  {:status "ok" :message "API инициализирован"})

(init-game)