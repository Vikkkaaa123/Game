(ns game.players.chat
  "Система чата между игроками"
  (:require [game.world :as world]
            [game.players.state :as player-state]
            [clojure.string :as str]))

;; Хранилище истории сообщений
(defonce chat-history (atom []))

(defn say-to-room!
  "Игрок говорит что-то в комнате"
  [player-name message]
  (let [room (world/get-player-room player-name)
        other-players (disj (world/get-room-players room) player-name)
        chat-entry {:from player-name
                    :message message
                    :room room
                    :timestamp (System/currentTimeMillis)}]
    
    ;; Сохраняем в историю
    (swap! chat-history conj chat-entry)
    
    ;; Обновляем статистику
    (player-state/increment-messages player-name)
    (player-state/update-last-action player-name)
    
    ;; Формируем результат
    {:success true
     :message (str player-name " говорит: " message)
     :broadcast-to (count other-players)
     :players (vec other-players)
     :chat-entry chat-entry}))

(defn tell-player!
  "Личное сообщение другому игроку"
  [from-player to-player message]
  (if (= (world/get-player-room from-player) (world/get-player-room to-player))
    (let [chat-entry {:from from-player
                      :to to-player
                      :message message
                      :timestamp (System/currentTimeMillis)}]
      
      (swap! chat-history conj chat-entry)
      (player-state/increment-messages from-player)
      
      {:success true
       :message (str "[Лично для " to-player "] " message)
       :private true})
    {:error true
     :message (str "Игрок " to-player " не в вашей комнате")}))

(defn get-room-chat-history
  "История чата комнаты (последние N сообщений)"
  [room-key limit]
  (let [relevant (filter #(= (:room %) room-key) @chat-history)]
    (take-last limit (sort-by :timestamp relevant))))

(defn format-chat-message
  "Форматирование сообщения для отображения"
  [msg]
  (let [time (java.time.Instant/ofEpochMilli (:timestamp msg))
        formatter (java.time.format.DateTimeFormatter/ofPattern "HH:mm")]
    (str "[" (.format time formatter) "] "
         (:from msg) ": " (:message msg))))