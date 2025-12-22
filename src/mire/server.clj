(ns mire.server
  (:require [clojure.java.io :as io]
            [server.socket :as socket]
            [mire.player :as player]
            [mire.commands :as commands]
            [mire.rooms :as rooms]
            [game.world :as world]))

(defn- cleanup [player-name]
  (when player-name
    (world/remove-player! player-name)
    (commute player/streams dissoc player-name)))

(defn- get-unique-player-name [name]
  (if (world/player-exists? name)
    (do (print "Имя занято, попробуйте другое: ") (flush) (recur (read-line)))
    name))

(defn- mire-handle-client [in out]
  (binding [*in* (io/reader in)
            *out* (io/writer out)]
    
    (print "Введите ваше имя: ") (flush)
    (binding [player/*name* (get-unique-player-name (read-line))]
      (when player/*name*
        ;; Используем общий мир
        (world/add-player! player/*name*)
        
        (println (str "Добро пожаловать, " player/*name* "!"))
        (println "Введите 'help' для списка команд.")
        
        (try (loop [input (read-line)]
               (when input
                 (let [response (commands/execute input)]
                   (println response))
                 (print "> ") (flush)
                 (recur (read-line))))
             (finally (cleanup player/*name*)))))))

(defn -main
  ([port dir]
     (rooms/add-rooms dir)
     (defonce server (socket/create-server (Integer. port) mire-handle-client))
     (println "Запуск Mire сервера на порту" port))
  ([port] (-main port "resources/rooms"))
  ([] (-main 3333)))