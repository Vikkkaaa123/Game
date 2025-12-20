(ns game.players.state
  "Управление состоянием игроков через STM.
   Отдельный ref для игроков, синхронизированный с основным миром."
  (:require [game.game.world :as world]))

;; ГЛАВНОЕ СОСТОЯНИЕ ИГРОКОВ (отдельный ref для демонстрации распределенного STM)
(defonce players-state
  (ref {
    ;; Подключенные игроки и их онлайн статус
    :connected {}
    
    ;; Статистика игроков
    :stats {}
    
    ;; Игровые сессии и время
    :sessions {}
  }))

;; ФУНКЦИИ ДЛЯ ПОДКЛЮЧЕНИЯ/ОТКЛЮЧЕНИЯ
(defn connect-player!
  "Подключить нового игрока"
  [player-name]
  (dosync
    ;; Добавляем в список подключенных
    (alter players-state assoc-in [:connected player-name]
           {:connected-at (System/currentTimeMillis)
            :last-action (System/currentTimeMillis)
            :online true})
    
    ;; Создаем новую сессию
    (alter players-state assoc-in [:sessions player-name]
           {:current-session-start (System/currentTimeMillis)
            :total-play-time 0})
    
    ;; Инициализируем статистику
    (alter players-state assoc-in [:stats player-name]
           {:commands-executed 0
            :items-taken 0
            :rooms-visited #{:start}
            :puzzles-solved 0
            :items-dropped 0
            :distance-traveled 0})
    
    ;; Также добавляем игрока в основной мир
    (world/add-player! player-name)))

(defn disconnect-player!
  "Отключить игрока"
  [player-name]
  (dosync
    (let [now (System/currentTimeMillis)
          session-start (get-in @players-state [:sessions player-name :current-session-start])]
      
      ;; Обновляем общее время игры
      (when session-start
        (let [session-duration (- now session-start)]
          (alter players-state update-in [:sessions player-name :total-play-time]
                 + session-duration)))
      
      ;; Помечаем как оффлайн
      (alter players-state assoc-in [:connected player-name :online] false)
      (alter players-state assoc-in [:connected player-name :disconnected-at] now)
      
      ;; Удаляем из основного мира
      (world/remove-player! player-name))))

(defn update-last-action!
  "Обновить время последнего действия игрока"
  [player-name]
  (dosync
    (alter players-state assoc-in [:connected player-name :last-action]
           (System/currentTimeMillis))))

(defn increment-stat!
  "Увеличить статистику игрока"
  [player-name stat-key & [amount]]
  (dosync
    (let [inc-amount (or amount 1)]
      (alter players-state update-in [:stats player-name stat-key]
             (fn [current] (+ (or current 0) inc-amount))))))

(defn add-visited-room!
  "Добавить комнату в список посещенных"
  [player-name room-name]
  (dosync
    (alter players-state update-in [:stats player-name :rooms-visited]
           conj room-name)
    ;; Также увеличиваем счетчик пройденного расстояния
    (increment-stat! player-name :distance-traveled)))

(defn get-player-stats
  "Получить статистику игрока"
  [player-name]
  (get-in @players-state [:stats player-name]))

(defn get-connected-players
  "Получить список подключенных игроков"
  []
  (let [connected (get-in @players-state [:connected])]
    (filter #(get-in % [:online]) (vals connected))))

(defn player-online?
  "Проверить, онлайн ли игрок"
  [player-name]
  (get-in @players-state [:connected player-name :online]))

(defn get-player-play-time
  "Получить общее время игры игрока в секундах"
  [player-name]
  (let [total-ms (get-in @players-state [:sessions player-name :total-play-time] 0)
        session-start (get-in @players-state [:sessions player-name :current-session-start])
        current-session-duration (if (and session-start (player-online? player-name))
                                   (- (System/currentTimeMillis) session-start)
                                   0)]
    (/ (+ total-ms current-session-duration) 1000.0)))

(defn get-top-players
  "Получить топ игроков по статистике"
  [stat-key limit]
  (let [stats (get-in @players-state [:stats])
        sorted (sort-by (fn [[name data]] (get data stat-key 0)) > stats)]
    (take limit sorted)))

(defn reset-all-stats!
  "Сбросить всю статистику (для тестов)"
  []
  (dosync
    (ref-set players-state
             {:connected {}
              :stats {}
              :sessions {}})))

;; Экспорт ключевых функций
(defn init-players-system
  "Инициализация системы игроков"
  []
  (println "[PLAYERS] Система игроков инициализирована с STM"))

;; Автоматическая инициализация при загрузке
(init-players-system)
