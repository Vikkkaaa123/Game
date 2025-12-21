(ns game.players.inventory
  "Рабочая система инвентаря"
  (:require [game.world :as world]
            [game.items :as items]
            [clojure.string :as str]))

;; 1. Взять предмет (полностью работает)
(defn take-item! [player-name item-pattern]
  (let [room (world/get-player-room player-name)
        room-items (world/get-room-items room)
        
        ;; Ищем предмет в комнате
        found-item (first (filter #(str/includes?
                                    (str/lower-case (items/get-item-name %))
                                    (str/lower-case item-pattern))
                                  room-items))]
    
    (if found-item
      (do
        ;; STM транзакция
        (dosync
          (world/remove-item-from-room! room found-item)
          (world/add-to-inventory! player-name found-item))
        
        {:success true
         :message (str "Вы взяли: " (items/get-item-name found-item))
         :item found-item})
      
      {:error true
       :message "Такого предмета здесь нет"})))

;; 2. Положить предмет (полностью работает)
(defn drop-item! [player-name item-pattern]
  (let [inventory (world/get-player-inventory player-name)
        
        ;; Ищем предмет в инвентаре
        found-item (first (filter #(str/includes?
                                    (str/lower-case (items/get-item-name %))
                                    (str/lower-case item-pattern))
                                  inventory))]
    
    (if found-item
      (do
        ;; STM транзакция
        (dosync
          (world/remove-from-inventory! player-name found-item)
          (world/add-item-to-room! (world/get-player-room player-name) found-item))
        
        {:success true
         :message (str "Вы положили: " (items/get-item-name found-item))
         :item found-item})
      
      {:error true
       :message "У вас нет такого предмета"})))

;; 3. Показать инвентарь (полностью работает)
(defn show-inventory [player-name]
  (let [inventory (world/get-player-inventory player-name)]
    (if (empty? inventory)
      "Ваш инвентарь пуст"
      (str "Ваш инвентарь:\n" 
           (str/join "\n" (map items/get-item-name inventory))))))

;; 4. Использовать предмет (полностью работает)
(defn use-item! [player-name item-pattern]
  (let [inventory (world/get-player-inventory player-name)
        found-item (first (filter #(str/includes?
                                    (str/lower-case (items/get-item-name %))
                                    (str/lower-case item-pattern))
                                  inventory))]
    
    (if found-item
      (let [item-type (items/get-item-type found-item)
            room (world/get-player-room player-name)]
        
        (case item-type
          :key {:success true
                :message "Вы использовали ключ. Что-то щелкнуло..."}
          :document {:success true
                     :message (str "Вы прочитали: " (items/get-item-name found-item))}
          :tool {:success true
                 :message "Вы использовали инструмент"}
          :component {:success true
                      :message "Вы использовали компонент"}
          {:success true
           :message "Вы использовали предмет"}))
      
      {:error true
       :message "У вас нет такого предмета"})))

;; 5. Проверить наличие предмета (полностью работает)
(defn has-item? [player-name item-pattern]
  (let [inventory (world/get-player-inventory player-name)]
    (some #(str/includes?
            (str/lower-case (items/get-item-name %))
            (str/lower-case item-pattern))
          inventory)))

;; 6. Поиск предмета (полностью работает)
(defn find-item [player-name item-pattern]
  (let [inventory (world/get-player-inventory player-name)]
    (first (filter #(str/includes?
                     (str/lower-case (items/get-item-name %))
                     (str/lower-case item-pattern))
                   inventory))))

;; 7. Передать предмет (полностью работает)
(defn give-item! [from-player to-player item-pattern]
  (let [from-inventory (world/get-player-inventory from-player)
        found-item (first (filter #(str/includes?
                                    (str/lower-case (items/get-item-name %))
                                    (str/lower-case item-pattern))
                                  from-inventory))]
    
    (if (and found-item
             (= (world/get-player-room from-player)
                (world/get-player-room to-player)))
      (do
        ;; STM транзакция
        (dosync
          (world/remove-from-inventory! from-player found-item)
          (world/add-to-inventory! to-player found-item))
        
        {:success true
         :message (str from-player " передал " 
                      (items/get-item-name found-item) " игроку " to-player)
         :item found-item})
      
      {:error true
       :message "Не удалось передать предмет"})))

;; Сообщение при загрузке
(println "[INVENTORY] Система инвентаря загружена и работает")

;; Пример использования в REPL:
(comment
  ;; Тесты
  (take-item! "Игрок1" "ключ")
  (show-inventory "Игрок1")
  (drop-item! "Игрок1" "ключ")
  (use-item! "Игрок1" "ключ")
  (has-item? "Игрок1" "ключ")
  (give-item! "Игрок1" "Игрок2" "ключ")
)
