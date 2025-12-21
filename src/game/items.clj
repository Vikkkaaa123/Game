(ns game.items
  "Система предметов игры.
   Управление свойствами предметов, их взаимодействием и STM состоянием."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))



;; БАЗА ДАННЫХ ПРЕДМЕТОВ (загружается из .edn файлов)

(defonce items-db (atom {}))

(defn load-items-from-edn
  "Загрузить предметы из .edn файлов"
  []
  (println "[ITEMS] Загрузка предметов из EDN файлов...")
  
  (let [item-files ["keycard.edn" "microscope.edn" "wire.edn" 
                    "journal.edn" "blueprint.edn" "formulas.edn" "console.edn"]
        loaded-items (atom {})]
    
    (doseq [item-file item-files]
      (let [path (str "resources/items/" item-file)]
        (when (.exists (io/file path))
          (try
            (let [item-data (edn/read-string (slurp path))
                  item-key (keyword (str/replace item-file #"\.edn$" ""))]
              (swap! loaded-items assoc item-key item-data)
              (println "  ✓ Загружен:" (:name item-data)))
            (catch Exception e
              (println "  ✗ Ошибка загрузки" item-file ":" e))))))
    
    (reset! items-db @loaded-items)
    (println "  Всего предметов:" (count @items-db))
    @loaded-items))

;; Загружаем при старте
(load-items-from-edn)



;; STM СОСТОЯНИЕ ПРЕДМЕТОВ

(defonce items-state
  (ref {
    ;; Состояния предметов
    :conditions {
      :keycard {:used false :charged true}
      :wire {:connected false :insulation-good true}
      :console {:repaired false :powered false}
      :microscope {:examined false}
      :journal {:read false}
      :blueprint {:read false}
      :formulas {:read false}
    }
    
    ;; Позиции предметов
    :positions {
      :keycard :laboratory
      :wire :laboratory
      :microscope :laboratory
      :blueprint :hallway_ru
      :journal :archive
      :formulas :archive
      :console :console_room
    }
    
    ;; История использования
    :usage-history []
  }))



;; ФУНКЦИИ ДЛЯ РАБОТЫ С ПРЕДМЕТАМИ

(defn get-item
  "Получить данные предмета по ключу"
  [item-key]
  (get @items-db item-key))

(defn get-item-name
  "Получить отображаемое имя предмета"
  [item-key]
  (:name (get-item item-key)))

(defn get-item-desc
  "Получить описание предмета"
  [item-key]
  (:desc (get-item item-key)))

(defn item-exists?
  "Проверить существование предмета"
  [item-key]
  (contains? @items-db item-key))

(defn get-item-type
  "Получить тип предмета"
  [item-key]
  (:type (get-item item-key)))

(defn is-item-fixed?
  "Проверить, закреплен ли предмет (нельзя взять)"
  [item-key]
  (:fixed (get-item item-key) false))

(defn get-item-examination
  "Получить текст осмотра предмета"
  [item-key]
  (:examination (get-item item-key)))

(defn get-item-hint
  "Получить подсказку из предмета"
  [item-key]
  (:hint (get-item item-key)))

(defn get-item-effect
  "Получить эффект использования"
  [item-key]
  (:effect (get-item item-key)))




;; ФУНКЦИИ ДЛЯ РАБОТЫ СО СОСТОЯНИЕМ (STM)

(defn set-item-condition!
  "Установить состояние предмета"
  [item-key condition value]
  (dosync
    (alter items-state assoc-in [:conditions item-key condition] value)))

(defn get-item-condition
  "Получить состояние предмета"
  [item-key condition]
  (get-in @items-state [:conditions item-key condition]))

(defn mark-item-used!
  "Пометить предмет как использованный"
  [item-key player-name]
  (dosync
    (set-item-condition! item-key :used true)
    (set-item-condition! item-key :used-by player-name)
    (set-item-condition! item-key :used-time (System/currentTimeMillis))
    (alter items-state update :usage-history conj 
           {:item item-key
            :player player-name
            :time (System/currentTimeMillis)})))

(defn mark-item-examined!
  "Пометить предмет как осмотренный"
  [item-key player-name]
  (dosync
    (set-item-condition! item-key :examined true)
    (set-item-condition! item-key :examined-by player-name)
    (set-item-condition! item-key :examined-time (System/currentTimeMillis))))

(defn mark-item-read!
  "Пометить документ как прочитанный"
  [item-key player-name]
  (dosync
    (set-item-condition! item-key :read true)
    (set-item-condition! item-key :read-by player-name)
    (set-item-condition! item-key :read-time (System/currentTimeMillis))))

(defn get-item-position
  "Получить позицию предмета"
  [item-key]
  (get-in @items-state [:positions item-key]))

(defn set-item-position!
  "Установить позицию предмета"
  [item-key position]
  (dosync
    (alter items-state assoc-in [:positions item-key] position)))

(defn move-item!
  "Переместить предмет"
  [item-key from to]
  (dosync
    (alter items-state update :positions assoc item-key to)
    true))



;; ФУНКЦИИ ДЛЯ ИГРОВОЙ ЛОГИКИ

(defn can-use-item?
  "Проверить, можно ли использовать предмет в данной комнате"
  [item-key room-key]
  (let [usable-in (:usable-in (get-item item-key))]
    (and usable-in (contains? (set usable-in) room-key))))

(defn use-item!
  "Использовать предмет"
  [item-key player-name room-key]
  (if (can-use-item? item-key room-key)
    (do
      (mark-item-used! item-key player-name)
      {:success true
       :message (get-item-effect item-key)
       :item item-key
       :item-name (get-item-name item-key)})
    {:error true
     :message "Нельзя использовать этот предмет здесь"}))

(defn examine-item!
  "Осмотреть предмет"
  [item-key player-name]
  (mark-item-examined! item-key player-name)
  
  (let [examination (get-item-examination item-key)
        hint (get-item-hint item-key)
        already-examined (get-item-condition item-key :examined)]
    
    {:success true
     :item item-key
     :item-name (get-item-name item-key)
     :examination examination
     :hint hint
     :already-examined already-examined}))

(defn read-document!
  "Прочитать документ"
  [item-key player-name]
  (let [item-type (get-item-type item-key)]
    (if (= item-type :document)
      (do
        (mark-item-read! item-key player-name)
        {:success true
         :item item-key
         :item-name (get-item-name item-key)
         :content (or (:readable (get-item item-key))
                      (:desc (get-item item-key)))})
      {:error true
       :message "Это не документ"})))

(defn combine-items!
  "Скомбинировать два предмета"
  [item1-key item2-key player-name]
  (let [combine-with (:combine-with (get-item item1-key))]
    (if (and combine-with (contains? (set combine-with) item2-key))
      (do
        (dosync
          (mark-item-used! item1-key player-name)
          (mark-item-used! item2-key player-name)
          (set-item-condition! item1-key :combined-with item2-key)
          (set-item-condition! item2-key :combined-with item1-key))
        {:success true
         :message (str "Вы скомбинировали " (get-item-name item1-key) 
                      " и " (get-item-name item2-key))
         :items [item1-key item2-key]})
      {:error true
       :message "Эти предметы нельзя скомбинировать"})))

(defn find-item-by-name
  "Найти предмет по имени (частичному совпадению)"
  [name-pattern]
  (let [pattern-lower (str/lower-case name-pattern)]
    (first (filter (fn [[key data]]
                     (str/includes? (str/lower-case (:name data)) pattern-lower))
                   @items-db))))

(defn get-all-items
  "Получить все предметы"
  []
  (keys @items-db))

(defn get-items-by-room
  "Получить предметы в комнате"
  [room-key]
  (filter (fn [[key position]] (= position room-key))
          (get-in @items-state [:positions])))

(defn get-player-items
  "Получить предметы игрока (в инвентаре)"
  [player-inventory]
  ;; player-inventory - это набор ключей предметов
  (map (fn [item-key]
         {:key item-key
          :name (get-item-name item-key)
          :desc (get-item-desc item-key)})
       player-inventory))




;; ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ

(defn item-to-string
  "Преобразовать предмет в строку для отображения"
  [item-key]
  (let [item-data (get-item item-key)
        name (:name item-data)
        type (:type item-data)
        used (get-item-condition item-key :used)]
    (str name 
         (when used " (использован)")
         " [" (name type) "]")))

(defn format-item-list
  "Отформатировать список предметов"
  [item-keys]
  (if (empty? item-keys)
    "пусто"
    (str/join ", " (map item-to-string item-keys))))

(defn get-item-stats
  "Получить статистику по предметам"
  []
  (let [total-items (count @items-db)
        used-items (count (filter (fn [[key _]] (get-item-condition key :used))
                                  (get-in @items-state [:conditions])))
        examined-items (count (filter (fn [[key _]] (get-item-condition key :examined))
                                      (get-in @items-state [:conditions])))]
    
    {:total-items total-items
     :used-items used-items
     :examined-items examined-items
     :usage-history (count (get-in @items-state [:usage-history]))}))



;; ИНИЦИАЛИЗАЦИЯ

(defn init-items-system
  "Инициализировать систему предметов"
  []
  (println "[ITEMS] Система предметов инициализирована")
  (let [stats (get-item-stats)]
    (println "[ITEMS] Предметов в базе:" (:total-items stats))
    (println "[ITEMS] Использовано:" (:used-items stats))
    (println "[ITEMS] Осмотрено:" (:examined-items stats))))

;; Автоматическая инициализация
(init-items-system)




;; ДЛЯ РАБОТЫ В REPL

(comment
  ;; Проверить загрузку предметов
  (count @items-db)
  (keys @items-db)
  
  ;; Получить информацию о предмете
  (get-item :keycard)
  (get-item-name :keycard)
  (get-item-desc :wire)
  
  ;; Проверить состояния
  @items-state
  (get-item-condition :keycard :used)
  
  ;; Использовать предмет
  (use-item! :keycard "Алексей" :laboratory)
  (get-item-condition :keycard :used)
  
  ;; Осмотреть предмет
  (examine-item! :microscope "Мария")
  
  ;; Прочитать документ
  (read-document! :journal "Иван")
  
  ;; Получить статистику
  (get-item-stats)
  
  ;; Найти предмет по имени
  (find-item-by-name "ключ")
  (find-item-by-name "провод")
  
  ;; Отформатировать список
  (format-item-list [:keycard :wire :microscope])
)
