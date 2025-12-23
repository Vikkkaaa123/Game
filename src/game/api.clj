(ns game.api
  "API слой для веб-интерфейса"
  (:require [game.core :as game]
            [game.world :as world]
            [game.items :as items]
            [clojure.string :as str]))

;; Хранилище WebSocket каналов
(defonce websocket-channels (atom {}))

;; ========== ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ ==========

;; 1. Объявляем функции заранее (чтобы не было ошибок порядка)
(declare broadcast-to-room)

;; ========== ОСНОВНЫЕ ФУНКЦИИ ==========

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

;; 2. РАССЫЛКА СООБЩЕНИЙ В КОМНАТЕ
(defn broadcast-to-room [player-name message]
  (try
    (let [room-key (world/get-player-room player-name)
          other-players (disj (world/get-room-players room-key) player-name)]
      
      (println "[CHAT BROADCAST] От " player-name " к " (count other-players) " игрокам: " message)
      
      (doseq [other-player other-players]
        (when-let [chan (@websocket-channels other-player)]
          ;; TODO: Реализовать отправку через WebSocket
          (println "  -> Для " other-player ": " message)))
      
      {:status "ok" 
       :message (str "Сообщение отправлено " (count other-players) " игрокам")
       :recipients (vec other-players)})
    
    (catch Exception e
      {:error true :message (str "Ошибка рассылки: " (.getMessage e))})))

;; 3. ОБРАБОТКА КОМАНД - ПРОКСИ К game.core
(defn handle-web-command [player-name input]
  (try
    (if-not (world/player-exists? player-name)
      {:error true :message "Игрок не найден. Переподключитесь."}
      
      ;; Вызываем основную логику игры
      (let [response (game/handle-command player-name input)]
        
        ;; Проверяем тип ответа
        (if (and (map? response) (= (:type response) :chat-broadcast))
          ;; Это сообщение чата для рассылки
          (do
            ;; Рассылаем сообщение всем в комнате
            (broadcast-to-room player-name (:message response))
            ;; Возвращаем результат отправителю
            {:status "ok"
             :message (str "Вы сказали: \"" (:message response) "\"")
             :broadcast-count (:broadcast-to response)})
          ;; Обычная команда (response уже строка)
          {:status "ok" :message response})))
    
    (catch Exception e
      {:error true 
       :message (str "Ошибка выполнения команды: " (.getMessage e))})))

;; 4. ПОЛУЧЕНИЕ СОСТОЯНИЯ ИГРЫ
(defn get-game-state [player-name]
  (try
    (if-not (world/player-exists? player-name)
      {:error true :message "Игрок не найден"}
      (let [room-key (world/get-player-room player-name)
            room-data (world/get-room room-key)
            inventory (world/get-player-inventory player-name)]
        
        {:status "ok"
         :player {:name player-name
                  :room room-key
                  :inventory (mapv items/get-item-name inventory)}
         :room {:name (:name room-data)
                :desc (:desc room-data)
                :items (mapv items/get-item-name (world/get-room-items room-key))
                :players (vec (world/get-room-players room-key))}
         :timestamp (System/currentTimeMillis)}))
    
    (catch Exception e
      {:error true :message (str "Ошибка получения состояния: " (.getMessage e))})))

;; 5. ВЫХОД ИГРОКА
(defn logout-player [player-name]
  (try
    (world/remove-player! player-name)
    (swap! websocket-channels dissoc player-name)
    {:status "ok" :message "Игрок вышел"}
    (catch Exception e
      {:error true :message (str "Ошибка выхода: " (.getMessage e))})))

;; 6. ПОЛУЧЕНИЕ СПИСКА ИГРОКОВ
(defn get-online-players []
  (try
    {:status "ok"
     :players (vec (keys @websocket-channels))
     :count (count @websocket-channels)}
    (catch Exception e
      {:error true :message (str "Ошибка: " (.getMessage e))})))

;; 7. ПРОВЕРКА СТАТУСА ИГРОКА
(defn get-player-status [player-name]
  (try
    (if-let [channel (@websocket-channels player-name)]
      {:status "ok"
       :player player-name
       :online true
       :room (world/get-player-room player-name)}
      {:status "ok"
       :player player-name
       :online false})
    (catch Exception e
      {:error true :message (str "Ошибка: " (.getMessage e))})))

;; 8. ОТПРАВКА СООБЩЕНИЯ КОНКРЕТНОМУ ИГРОКУ
(defn send-to-player [player-name message]
  (try
    (if-let [channel (@websocket-channels player-name)]
      (do
        ;; TODO: Реализовать отправку через WebSocket
        (println "[PRIVATE MESSAGE] Для " player-name ": " message)
        {:status "ok" :message "Сообщение отправлено"})
      {:error true :message "Игрок не в сети"})
    (catch Exception e
      {:error true :message (str "Ошибка отправки: " (.getMessage e))})))

;; 9. ИНИЦИАЛИЗАЦИЯ API
(defn init-api []
  (println "[API] API слой инициализирован")
  (reset! websocket-channels {})
  {:status "ok" :message "API готов"})

;; Автоматическая инициализация при загрузке
(init-api)

;; Сообщение при загрузке модуля
(println "[API] Модуль API загружен")

;; Примеры использования для REPL
(comment
  ;; Тестирование API функций
  (register-player "test" nil)
  (handle-web-command "test" "look")
  (get-game-state "test")
  (get-online-players)
  (logout-player "test")
  (broadcast-to-room "test" "Привет всем!")
)