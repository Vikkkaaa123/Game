(ns game.api
  (:require [game.core :as game]
            [game.world :as world]
            [game.items :as items]
            [clojure.string :as str]))

(defonce sessions (atom {}))

(defn register-player [player-name channel]
  (try
    (if (world/player-exists? player-name)
      {:error true :message (str "Игрок " player-name " уже существует")}
      (do
        (world/add-player! player-name)
        (swap! sessions assoc player-name channel)
        {:status "ok" 
         :message (str "Игрок " player-name " зарегистрирован")
         :player player-name}))
    (catch Exception e
      {:error true :message (str "Ошибка:" (.getMessage e))})))

(defn handle-web-command [player-name input]
  (try
    (if-not (world/player-exists? player-name)
      {:error true :message "Игрок не найден"}
      (let [response (game/handle-command player-name input)
            room-key (world/get-player-room player-name)]
        {:status "ok"
         :message response
         :game-state {:player {:name player-name
                               :room room-key
                               :inventory (world/get-player-inventory player-name)}
                      :room {:name (world/get-room-name room-key)
                             :desc (world/get-room-desc room-key)
                             :items (world/get-room-items room-key)
                             :players (world/get-room-players room-key)}}}))
    (catch Exception e
      {:error true :message (str "Ошибка:" (.getMessage e))})))

(defn get-game-state [player-name]
  (try
    (if-not (world/player-exists? player-name)
      {:error true :message "Игрок не найден"}
      (let [room-key (world/get-player-room player-name)]
        {:status "ok"
         :player {:name player-name
                  :room room-key
                  :inventory (world/get-player-inventory player-name)}
         :room {:name (world/get-room-name room-key)
                :desc (world/get-room-desc room-key)
                :items (world/get-room-items room-key)
                :players (world/get-room-players room-key)}}))
    (catch Exception e
      {:error true :message (str "Ошибка:" (.getMessage e))})))

(defn broadcast-to-room [player-name message]
  (try
    (let [room-key (world/get-player-room player-name)
          other-players (world/get-players-in-room room-key player-name)]
      (doseq [player other-players]
        (when-let [channel (@sessions player)]
          ;; Отправка через WebSocket
          ))
      {:status "ok" :message (str "Сообщение отправлено")})
    (catch Exception e
      {:error true :message (str "Ошибка:" (.getMessage e))})))

(defn logout-player [player-name]
  (try
    (world/remove-player! player-name)
    (swap! sessions dissoc player-name)
    {:status "ok" :message "Игрок вышел"}
    (catch Exception e
      {:error true :message (str "Ошибка:" (.getMessage e))})))

(defn get-server-stats []
  {:players (count (world/get-all-players))
   :rooms (count (world/get-all-rooms))
   :sessions (count @sessions)})

(defn init-game []
  (println "Инициализация игры...")
  {:status "ok" :message "Игра инициализирована"})