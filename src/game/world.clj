(ns game.world
  "Ядро игрового мира - STM система.
   Только состояние комнат и игроков. Предметы в отдельном модуле."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))



;; ГЛАВНОЕ СОСТОЯНИЕ МИРА

(defonce world-state
  (ref {
    ;; КОМНАТЫ (загружаются из .edn)
    :rooms {}
    
    ;; ИГРОКИ {имя {:room :комната :inventory #{}}}
    :players {}
    
    ;; ЗАГРУЖЕНЫ ЛИ КОМНАТЫ
    :rooms-loaded false
  }))




;; ЗАГРУЗКА КОМНАТ ИЗ .edn

(defn load-rooms-from-edn
  "Загрузить комнаты из resources/rooms_ru/"
  []
  (println "[WORLD] Загрузка комнат из EDN файлов...")
  
  (let [room-files ["laboratory.edn" "hallway_ru.edn" "archive.edn" 
                    "console_room.edn" "exit.edn"]
        loaded-rooms (atom {})]
    
    (doseq [room-file room-files]
      (let [path (str "resources/rooms_ru/" room-file)]
        (when (.exists (io/file path))
          (try
            (let [room-data (edn/read-string (slurp path))
                  room-key (keyword (-> room-file
                                        (clojure.string/replace #"_ru\.edn$" "")
                                        (clojure.string/replace #"\.edn$" "")))]
              (swap! loaded-rooms assoc room-key room-data)
              (println "  ✓ Загружена:" (:name room-data)))
            (catch Exception e
              (println "  ✗ Ошибка загрузки" room-file ":" e))))))
    
    ;; Сохраняем в STM
    (dosync
      (alter world-state assoc :rooms @loaded-rooms)
      (alter world-state assoc :rooms-loaded true))
    
    (println "  Всего комнат:" (count @loaded-rooms))
    @loaded-rooms))

;; Автоматическая загрузка при старте
(load-rooms-from-edn)



;; БАЗОВЫЕ ФУНКЦИИ

(defn get-room
  "Получить комнату"
  [room-key]
  (get-in @world-state [:rooms room-key]))

(defn get-room-name
  "Получить имя комнаты"
  [room-key]
  (:name (get-room room-key)))

(defn get-room-desc
  "Получить описание комнаты"
  [room-key]
  (:desc (get-room room-key)))

(defn get-room-exits
  "Получить выходы из комнаты"
  [room-key]
  (:exits (get-room room-key)))

(defn get-room-items
  "Получить предметы в комнате"
  [room-key]
  (:items (get-room room-key)))

(defn get-room-players
  "Получить игроков в комнате"
  [room-key]
  (:players (get-room room-key)))

(defn get-all-rooms
  "Получить все комнаты"
  []
  (keys (:rooms @world-state)))




;; УПРАВЛЕНИЕ ИГРОКАМИ (STM)

(defn add-player!
  "Добавить игрока"
  [player-name]
  (dosync
    (when-not (get-in @world-state [:players player-name])
      ;; Добавляем игрока
      (alter world-state assoc-in [:players player-name]
             {:room :laboratory
              :inventory #{}
              :joined-at (System/currentTimeMillis)})
      
      ;; Добавляем в стартовую комнату
      (alter world-state update-in [:rooms :laboratory :players] 
             (fn [players] (conj (or players #{}) player-name)))
      
      (println "[WORLD] Игрок добавлен:" player-name)
      player-name)))

(defn remove-player!
  "Удалить игрока"
  [player-name]
  (dosync
    (let [room (get-in @world-state [:players player-name :room])]
      ;; Убираем из комнаты
      (when room
        (alter world-state update-in [:rooms room :players] disj player-name))
      
      ;; Убираем из списка игроков
      (alter world-state update-in [:players] dissoc player-name)
      
      (println "[WORLD] Игрок удален:" player-name)
      true)))

(defn get-player
  "Получить игрока"
  [player-name]
  (get-in @world-state [:players player-name]))

(defn player-exists?
  "Проверить, существует ли игрок"
  [player-name]
  (boolean (get-player player-name)))

(defn get-player-room
  "Получить комнату игрока"
  [player-name]
  (:room (get-player player-name)))

(defn set-player-room!
  "Установить комнату игрока"
  [player-name room-key]
  (dosync
    (let [old-room (get-player-room player-name)]
      (when (and old-room (get-room room-key))
        ;; Убираем из старой комнаты
        (alter world-state update-in [:rooms old-room :players] disj player-name)
        
        ;; Добавляем в новую комнату
        (alter world-state update-in [:rooms room-key :players] 
               (fn [players] (conj (or players #{}) player-name)))
        
        ;; Обновляем у игрока
        (alter world-state assoc-in [:players player-name :room] room-key)
        
        (println "[WORLD]" player-name "переместился из" old-room "в" room-key)
        true))))

(defn get-player-inventory
  "Получить инвентарь игрока"
  [player-name]
  (:inventory (get-player player-name)))

(defn add-to-inventory!
  "Добавить предмет в инвентарь"
  [player-name item-key]
  (dosync
    (alter world-state update-in [:players player-name :inventory] 
           (fn [inv] (conj (or inv #{}) item-key)))
    true))

(defn remove-from-inventory!
  "Удалить предмет из инвентаря"
  [player-name item-key]
  (dosync
    (alter world-state update-in [:players player-name :inventory] 
           (fn [inv] (disj (or inv #{}) item-key)))
    true))

(defn player-has-item?
  "Проверить, есть ли у игрока предмет"
  [player-name item-key]
  (contains? (get-player-inventory player-name) item-key))

(defn get-all-players
  "Получить всех игроков"
  []
  (keys (:players @world-state)))



;; РАБОТА С КОМНАТАМИ (STM)

(defn add-item-to-room!
  "Добавить предмет в комнату"
  [room-key item-key]
  (dosync
    (alter world-state update-in [:rooms room-key :items] 
           (fn [items] (conj (or items #{}) item-key)))
    true))

(defn remove-item-from-room!
  "Удалить предмет из комнаты"
  [room-key item-key]
  (dosync
    (alter world-state update-in [:rooms room-key :items] 
           (fn [items] (disj (or items #{}) item-key)))
    true))

(defn item-in-room?
  "Проверить, есть ли предмет в комнате"
  [room-key item-key]
  (contains? (get-room-items room-key) item-key))

(defn move-item!
  "Переместить предмет между комнатами"
  [from-room to-room item-key]
  (dosync
    (remove-item-from-room! from-room item-key)
    (add-item-to-room! to-room item-key)
    true))

(defn get-exit-room
  "Получить комнату по выходу"
  [from-room direction]
  (let [exits (get-room-exits from-room)]
    (get exits (keyword direction))))

(defn get-available-exits
  "Получить доступные выходы"
  [room-key]
  (let [exits (get-room-exits room-key)]
    (into {} (map (fn [[dir target]] [dir {:room target :locked false}]) exits))))

(defn get-players-in-room
  "Получить игроков в комнате (кроме указанного)"
  [room-key exclude-player]
  (disj (get-room-players room-key) exclude-player))



;; ИНИЦИАЛИЗАЦИЯ

(println "[WORLD] Модуль world.clj загружен")
(println "[WORLD] Загружено комнат:" (count (get-all-rooms)))
(println "[WORLD] Игроков онлайн:" (count (get-all-players)))

;; Для работы в REPL
(comment
  ;; Загрузить комнаты
  (load-rooms-from-edn)
  
  ;; Проверить комнаты
  (get-all-rooms)
  (get-room :laboratory)
  (get-room-name :laboratory)
  
  ;; Добавить игрока
  (add-player! "Тест")
  (get-player "Тест")
  (get-player-room "Тест")
  
  ;; Переместить игрока
  (set-player-room! "Тест" :hallway_ru)
  
  ;; Работа с инвентарем
  (add-to-inventory! "Тест" :keycard)
  (get-player-inventory "Тест")
  (player-has-item? "Тест" :keycard)
  (remove-from-inventory! "Тест" :keycard)
  
  ;; Работа с комнатами
  (add-item-to-room! :laboratory :wire)
  (item-in-room? :laboratory :wire)
  (remove-item-from-room! :laboratory :wire)
  
  ;; Получить выходы
  (get-exit-room :laboratory :north)
  (get-available-exits :laboratory)
  
  ;; Удалить игрока
  (remove-player! "Тест")
)