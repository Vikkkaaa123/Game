(ns mire.player
  (:require [game.world :as world]))

(def ^:dynamic *name* nil)
(def ^:dynamic *current-room* nil)
(def ^:dynamic *inventory* nil)

(def prompt "> ")
(def streams (ref {}))

(defn carrying? [thing]
  (world/player-has-item? *name* (keyword thing)))