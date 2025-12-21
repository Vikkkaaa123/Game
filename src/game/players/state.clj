(ns game.players.state
  "Состояние игроков - расширение world.clj"
  (:require [game.world :as world]))

;; Дополнительная статистика игроков (дополняет world.clj)
(defonce player-stats
  (atom {}))

;; Инициализировать статистику для игрока
(defn init-player-stats [player-name]
  (swap! player-stats assoc player-name
         {:joined-at (System/currentTimeMillis)
          :last-action (System/currentTimeMillis)
          :commands-executed 0
          :items-taken 0
          :rooms-visited #{:laboratory}
          :messages-sent 0}))

;; Обновить время последнего действия
(defn update-last-action [player-name]
  (swap! player-stats assoc-in [player-name :last-action] 
         (System/currentTimeMillis)))

;; Увеличить счетчик команд
(defn increment-commands [player-name]
  (swap! player-stats update-in [player-name :commands-executed] inc))

;; Увеличить счетчик взятых предметов
(defn increment-items-taken [player-name]
  (swap! player-stats update-in [player-name :items-taken] inc))

;; Добавить посещенную комнату
(defn add-visited-room [player-name room-key]
  (swap! player-stats update-in [player-name :rooms-visited] conj room-key))

;; Увеличить счетчик сообщений
(defn increment-messages [player-name]
  (swap! player-stats update-in [player-name :messages-sent] inc))

;; Получить статистику игрока
(defn get-player-stats [player-name]
  (let [stats (get @player-stats player-name)
        player (world/get-player player-name)
        room (world/get-player-room player-name)]
    
    (merge stats
           {:name player-name
            :room (world/get-room-name room)
            :inventory-count (count (:inventory player))
            :play-time-seconds (/ (- (System/currentTimeMillis) 
                                     (:joined-at stats))
                                  1000.0)})))

;; Получить игроков в комнате со статистикой
(defn get-players-in-room-with-stats [room-key]
  (let [players (world/get-room-players room-key)]
    (map get-player-stats players)))

;; Получить всех игроков онлайн
(defn get-all-online-players []
  (keys @player-stats))

;; Получить время онлайн игрока
(defn get-play-time [player-name]
  (let [stats (get @player-stats player-name)]
    (if stats
      (/ (- (System/currentTimeMillis) (:joined-at stats)) 1000.0)
      0)))

;; Сбросить статистику (для тестов)
(defn reset-stats [player-name]
  (swap! player-stats dissoc player-name))

;; Сбросить всю статистику
(defn reset-all-stats []
  (reset! player-stats {}))

;; Сообщение при загрузке
(println "[PLAYER-STATS] Система статистики игроков загружена")

;; Для REPL
(comment
  ;; Тестирование
  (init-player-stats "Тест")
  (update-last-action "Тест")
  (increment-commands "Тест")
  (get-player-stats "Тест")
  (get-play-time "Тест")
  (reset-stats "Тест")
)
