(ns game.players.inventory
  "Управление инвентарями игроков через STM.
   Каждый инвентарь - отдельный ref для демонстрации nested STM."
  (:require [game.game.world :as world]
            [game.game.items :as items]
            [game.players.state :as players]))

;; ОТДЕЛЬНЫЕ REF ДЛЯ КАЖДОГО ИНВЕНТАРЯ (nested STM)
(def inventories-refs (ref {}))

(defn create-inventory-ref!
  "Создать отдельный ref для инвентаря игрока"
  [player-name]
  (dosync
    (when-not (get @inventories-refs player-name)
      (alter inventories-refs assoc player-name (ref #{})))
    (get @inventories-refs player-name)))

(defn get-inventory-ref
  "Получить ref инвентаря игрока"
  [player-name]
  (or (get @inventories-refs player-name)
      (create-inventory-ref! player-name)))

(defn get-inventory
  "Получить содержимое инвентаря игрока"
  [player-name]
  @(get-inventory-ref player-name))

(defn add-to-inventory!
  "Добавить предмет в инвентарь игрока (STM транзакция)"
  [player-name item-name]
  (dosync
    (let [inventory-ref (get-inventory-ref player-name)
          player-room (world/get-player-room player-name)]
      
      ;; Проверяем, что предмет есть в комнате
      (when (and (items/item-exists? item-name)
                 (not (items/is-item-fixed? item-name))
                 (world/item-in-room? player-room item-name))
        
        ;; Удаляем предмет из комнаты
        (world/remove-item-from-room! player-room item-name)
        
        ;; Добавляем в инвентарь
        (alter inventory-ref conj item-name)
        
        ;; Обновляем статистику
        (players/increment-stat! player-name :items-taken)
        
        ;; Обновляем позицию предмета
        (items/update-item-position! item-name :inventory)
        
        ;; Логируем действие
        (println "[INVENTORY]" player-name "взял" item-name)
        true))))

(defn remove-from-inventory!
  "Удалить предмет из инвентаря игрока"
  [player-name item-name]
  (dosync
    (let [inventory-ref (get-inventory-ref player-name)
          player-room (world/get-player-room player-name)]
      
      ;; Проверяем, что предмет есть в инвентаре
      (when (contains? @inventory-ref item-name)
        
        ;; Удаляем из инвентаря
        (alter inventory-ref disj item-name)
        
        ;; Добавляем в комнату
        (world/add-item-to-room! player-room item-name)
        
        ;; Обновляем статистику
        (players/increment-stat! player-name :items-dropped)
        
        ;; Обновляем позицию предмета
        (items/update-item-position! item-name player-room)
        
        ;; Логируем действие
        (println "[INVENTORY]" player-name "положил" item-name)
        true))))

(defn move-item-between-inventories!
  "Передать предмет другому игроку (сложная STM транзакция)"
  [from-player to-player item-name]
  (dosync
    (let [from-inventory-ref (get-inventory-ref from-player)
          to-inventory-ref (get-inventory-ref to-player)
          from-room (world/get-player-room from-player)
          to-room (world/get-player-room to-player)]
      
      ;; Проверяем условия
      (when (and (= from-room to-room)  ;; Игроки в одной комнате
                 (contains? @from-inventory-ref item-name)
                 (not (items/is-item-fixed? item-name)))
        
        ;; Перемещаем предмет
        (alter from-inventory-ref disj item-name)
        (alter to-inventory-ref conj item-name)
        
        ;; Обновляем статистику
        (players/increment-stat! from-player :items-given)
        (players/increment-stat! to-player :items-received)
        
        ;; Логируем
        (println "[INVENTORY]" from-player "передал" item-name "к" to-player)
        true))))

(defn has-item?
  "Проверить, есть ли у игрока предмет"
  [player-name item-name]
  (contains? (get-inventory player-name) item-name))

(defn inventory-weight
  "Вычислить вес инвентаря игрока"
  [player-name]
  (let [inventory-items (get-inventory player-name)]
    (reduce (fn [total item-name]
              (+ total (or (items/get-item-weight item-name) 0)))
            0
            inventory-items)))

(defn max-inventory-weight
  "Максимальный вес инвентаря"
  [player-name]
  20.0)  ;; Фиксированный лимит

(defn can-carry?
  "Проверить, может ли игрок взять предмет"
  [player-name item-name]
  (let [current-weight (inventory-weight player-name)
        item-weight (items/get-item-weight item-name)
        max-weight (max-inventory-weight player-name)]
    (<= (+ current-weight item-weight) max-weight)))

(defn inventory-full?
  "Проверить, полон ли инвентарь"
  [player-name]
  (let [current-weight (inventory-weight player-name)
        max-weight (max-inventory-weight player-name)]
    (>= current-weight max-weight)))

(defn use-item-from-inventory!
  "Использовать предмет из инвентаря"
  [player-name item-name]
  (dosync
    (let [player-room (world/get-player-room player-name)]
      
      ;; Проверяем наличие предмета
      (when (has-item? player-name item-name)
        
        ;; Проверяем возможность использования в текущей комнате
        (if (items/can-use-item? item-name player-room)
          
          ;; Используем предмет
          (do
            (items/use-item! item-name player-name player-room)
            
            ;; Некоторые предметы расходуются
            (when (items/get-item-condition item-name :consumable)
              (remove-from-inventory! player-name item-name))
            
            ;; Обновляем статистику
            (players/increment-stat! player-name :items-used)
            
            {:success true
             :message "Предмет использован"
             :item item-name})
          
          {:success false
           :message "Нельзя использовать этот предмет здесь"
           :item item-name})))))

(defn combine-items!
  "Скомбинировать два предмета из инвентаря"
  [player-name item1 item2]
  (dosync
    (let [player-room (world/get-player-room player-name)]
      
      ;; Проверяем наличие обоих предметов
      (when (and (has-item? player-name item1)
                 (has-item? player-name item2)
                 (items/can-combine? item1 item2))
        
        ;; Записываем комбинацию
        (items/record-combination! item1 item2 player-name)
        
        ;; Удаляем исходные предметы
        (remove-from-inventory! player-name item1)
        (remove-from-inventory! player-name item2)
        
        ;; Создаем новый предмет (пример)
        (let [new-item "усиленный-провод"]
          (when (items/item-exists? new-item)
            (add-to-inventory! player-name new-item)))
        
        ;; Обновляем статистику
        (players/increment-stat! player-name :items-combined)
        
        {:success true
         :message "Предметы скомбинированы"
         :combined [item1 item2]}))))

(defn list-inventory
  "Получить детальный список инвентаря"
  [player-name]
  (let [inventory-items (get-inventory player-name)
        current-weight (inventory-weight player-name)
        max-weight (max-inventory-weight player-name)]
    
    {:player player-name
     :items (map (fn [item-name]
                   {:name item-name
                    :display-name (items/get-item-name item-name)
                    :weight (items/get-item-weight item-name)
                    :type (items/get-item-type item-name)})
                 inventory-items)
     :total-items (count inventory-items)
     :current-weight (format "%.2f" current-weight)
     :max-weight max-weight
     :capacity-percent (int (* 100 (/ current-weight max-weight)))}))

(defn search-inventory
  "Поиск предметов в инвентаре по критериям"
  [player-name criteria]
  (let [inventory-items (get-inventory player-name)]
    (filter (fn [item-name]
              (let [item (items/get-item item-name)]
                (case (:type criteria)
                  :type (= (:type item) (:value criteria))
                  :name (clojure.string/includes? 
                         (clojure.string/lower-case (:name item))
                         (clojure.string/lower-case (:value criteria)))
                  :weight (<= (or (:weight item) 0) (:value criteria))
                  true)))
            inventory-items)))

(defn transfer-all-to-room!
  "Выложить все предметы из инвентаря в комнату"
  [player-name]
  (dosync
    (let [inventory-items (get-inventory player-name)
          player-room (world/get-player-room player-name)]
      
      (doseq [item-name inventory-items]
        (remove-from-inventory! player-name item-name))
      
      {:success true
       :items-dropped (count inventory-items)
       :room player-room})))

(defn clear-inventory!
  "Очистить инвентарь игрока (для тестов)"
  [player-name]
  (dosync
    (let [inventory-ref (get-inventory-ref player-name)]
      (ref-set inventory-ref #{}))))

(defn get-inventory-size
  "Получить размер инвентаря"
  [player-name]
  (count (get-inventory player-name)))

;; ИНИЦИАЛИЗАЦИЯ ИНВЕНТАРЕЙ ПРИ ПОДКЛЮЧЕНИИ ИГРОКА
(defn init-player-inventory
  "Инициализировать инвентарь для нового игрока"
  [player-name]
  (create-inventory-ref! player-name)
  
  ;; Даем стартовые предметы (опционально)
  (dosync
    (when (< (count (get-inventory player-name)) 2)
      ;; Можно добавить стартовые предметы
      ;; (add-to-inventory! player-name "фонарик")
      ))
  
  (println "[INVENTORY] Инвентарь создан для" player-name))

;; УТИЛИТЫ ДЛЯ ОТЛАДКИ
(defn debug-print-all-inventories
  "Вывести все инвентари для отладки"
  []
  (println "\n=== ВСЕ ИНВЕНТАРИ ===")
  (doseq [[player-name inventory-ref] @inventories-refs]
    (let [inventory @inventory-ref
          weight (inventory-weight player-name)]
      (println player-name "(" (format "%.1f" weight) "kg):" 
               (clojure.string/join ", " inventory))))
  (println "===================\n"))

;; ЭКСПОРТ
(defn init-inventory-system
  "Инициализировать систему инвентарей"
  []
  (println "[INVENTORY] Система инвентарей инициализирована с nested STM refs"))

(init-inventory-system)
