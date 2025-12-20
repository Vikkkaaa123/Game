(ns game.adapters.mire-player
  "Адаптер для замены функций оригинального mire/player.clj"
  (:require [game.game.world :as world]
            [game.players.state :as players]
            [game.players.inventory :as inventory]))

;; Просто заглушка для совместимости
(defn add-player [name]
  (println "[ADAPTER] Игрок добавлен через адаптер:" name)
  (players/connect-player! name))

(defn remove-player [name]
  (println "[ADAPTER] Игрок удален через адаптер:" name)
  (players/disconnect-player! name))

(defn player-rooms []
  {})

(defn load-player [name]
  nil)

;; Для совместимости с оригинальным кодом
(def ^:export *players* (atom {}))
