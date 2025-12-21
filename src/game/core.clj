(ns game.core
  "Главный запускаемый файл игры.
   Запуск: lein run -m game.core"
  (:require [game.world :as world]
            [game.items :as items]
            [clojure.string :as str]))

;; Флаг работы игры
(defonce game-running (atom true))

;; Обработка команд игрока
(defn handle-command [player-name input]
  (try
    (let [parts (str/split input #" ")
          ;; ИСПРАВЛЕНИЕ 1: обрезаем пробелы и приводим к нижнему регистру
          command (str/trim (str/lower-case (first parts)))
          args (str/join " " (rest parts))]
      
      (cond
        ;; ИСПРАВЛЕНИЕ 2: все варианты команд через cond вместо case
        ;; Осмотреться
        (contains? #{"look" "осмотреть" "посмотреть" "осмотреться" "l"} command)
        (let [room (world/get-player-room player-name)]
          (str "Вы в " (world/get-room-name room) "\n"
               (world/get-room-desc room) "\n"
               "Предметы: " (if-let [room-items (world/get-room-items room)]
                              (if (empty? room-items)
                                "нет"
                                ;; ИСПРАВЛЕНИЕ 3: правильное получение имен предметов
                                (let [item-names (keep items/get-item-name room-items)]
                                  (if (empty? item-names)
                                    "нет"
                                    (str/join ", " item-names))))
                              "нет") "\n"
               "Игроки: " (if-let [players (world/get-room-players room)]
                            (let [other-players (disj players player-name)]
                              (if (empty? other-players)
                                "никого"
                                (str/join ", " other-players)))
                            "никого")))
        
        ;; Взять предмет
        (contains? #{"взять" "take" "поднять" "t"} command)
        (if (str/blank? args)
          "Укажите предмет: взять [предмет]"
          (let [room (world/get-player-room player-name)
                room-items (world/get-room-items room)
                ;; Ищем предмет по частичному совпадению имени
                item-key (first (filter #(let [item-name (items/get-item-name %)]
                                           (and item-name 
                                                (str/includes? 
                                                 (str/lower-case item-name) 
                                                 (str/lower-case args))))
                                        room-items))]
            (if item-key
              (do
                (world/add-to-inventory! player-name item-key)
                (world/remove-item-from-room! room item-key)
                (str "Вы взяли: " (items/get-item-name item-key)))
              "Такого предмета здесь нет")))
        
        ;; Положить предмет
        (contains? #{"положить" "drop" "бросить" "пол"} command)
        (if (str/blank? args)
          "Укажите предмет: положить [предмет]"
          (let [inventory (world/get-player-inventory player-name)
                ;; Ищем предмет в инвентаре
                item-key (first (filter #(let [item-name (items/get-item-name %)]
                                           (and item-name
                                                (str/includes?
                                                 (str/lower-case item-name)
                                                 (str/lower-case args))))
                                        inventory))]
            (if item-key
              (do
                (world/remove-from-inventory! player-name item-key)
                (let [room (world/get-player-room player-name)]
                  (world/add-item-to-room! room item-key))
                (str "Вы положили: " (items/get-item-name item-key)))
              "У вас нет такого предмета")))
        
        ;; Показать инвентарь
        (contains? #{"инвентарь" "inventory" "инв" "i"} command)
        (let [inventory (world/get-player-inventory player-name)]
          (if (empty? inventory)
            "Ваш инвентарь пуст"
            (str "Ваш инвентарь:\n" 
                 (str/join "\n" (keep items/get-item-name inventory)))))
        
        ;; Перемещение
        (contains? #{"идти" "go" "g"} command)
        (if (str/blank? args)
          "Укажите направление: идти [север/юг/запад/восток]"
          (let [direction-map {"север" :north "юг" :south "запад" :west "восток" :east
                               "north" :north "south" :south "west" :west "east" :east}
                direction (get direction-map (str/lower-case args))
                current-room (world/get-player-room player-name)
                target-room (when direction (world/get-exit-room current-room direction))]
            (if target-room
              (do
                (world/set-player-room! player-name target-room)
                (handle-command player-name "look"))
              "Нельзя пойти в этом направлении")))
        
        ;; Короткие команды движения
        (contains? #{"с" "север" "north" "n"} command) 
        (handle-command player-name "идти север")
        
        (contains? #{"ю" "юг" "south" "s"} command)
        (handle-command player-name "идти юг")
        
        (contains? #{"з" "запад" "west" "w"} command)
        (handle-command player-name "идти запад")
        
        (contains? #{"в" "восток" "east" "e"} command)
        (handle-command player-name "идти восток")
        
        ;; Сказать в комнате
        (contains? #{"сказать" "say" "с"} command)
        (if (str/blank? args)
          "Скажите что-нибудь: сказать [текст]"
          (let [room (world/get-player-room player-name)
                other-players (world/get-players-in-room room player-name)]
            (str "Вы сказали: \"" args "\"\n"
                 (if (empty? other-players)
                   "Но в комнате никого нет"
                   (str "Вас слышат: " (str/join ", " other-players))))))
        
        ;; Помощь
        (contains? #{"помощь" "help" "справка" "h" "?"} command)
        (str "Доступные команды:\n"
             "look / осмотреть - осмотреться\n"
             "взять [предмет] / take - взять предмет\n"
             "положить [предмет] / drop - положить предмет\n"
             "инвентарь / inventory - показать инвентарь\n"
             "идти [север/юг/запад/восток] / go - переместиться\n"
             "с/ю/з/в / n/s/w/e - короткие команды движения\n"
             "сказать [текст] / say - сказать в комнате\n"
             "помощь / help - эта справка\n"
             "выход / exit - выйти из игры")
        
        ;; Выход из игры
        (contains? #{"выход" "exit" "quit" "выйти" "q"} command)
        (do
          (world/remove-player! player-name)
          (reset! game-running false)
          "До свидания!")
        
        ;; Неизвестная команда
        :else
        "Неизвестная команда. Введите 'помощь' для списка команд."))
    
    (catch Exception e
      (str "Ошибка выполнения команды: " (.getMessage e)))))

;; Главная функция игры
(defn -main
  "Точка входа в игру"
  [& args]
  (println "🎮 ИГРА 'ПОБЕГ ИЗ ЛАБОРАТОРИИ'")
  (println "==============================")
  (println "Кооперативная текстовая игра на Clojure с STM")
  (println "Создано командой разработчиков")
  
  (println "\nДобро пожаловать!")
  (print "Введите ваше имя: ")
  (flush)
  
  (let [player-name (read-line)]
    (when (and player-name (not (str/blank? player-name)))
      ;; Добавляем игрока в мир
      (world/add-player! player-name)
      
      (println (str "\nЗдравствуйте, " player-name "!"))
      (println "Вы находитесь в заброшенной лаборатории.")
      (println "Введите 'помощь' для списка команд.")
      (println (handle-command player-name "look"))
      
      ;; Главный игровой цикл
      (while @game-running
        (print "\n> ")
        (flush)
        
        (let [input (read-line)]
          (when (and input (not (str/blank? input)))
            (let [result (handle-command player-name input)]
              (println result))))))
    
    (println "\nИгра завершена. Спасибо за игру!")))

;; Сообщение при загрузке модуля
(println "[CORE] Основной модуль игры загружен")

;; Для работы в REPL (без запуска сервера)
(comment
  ;; Тестирование в REPL
  (require '[game.world :as world])
  (require '[game.items :as items])
  
  ;; 1. Добавить игрока
  (world/add-player! "Тест")
  
  ;; 2. Посмотреть комнату
  (handle-command "Тест" "look")
  (handle-command "Тест" "осмотреть")
  (handle-command "Тест" "l")
  
  ;; 3. Взять предмет
  (handle-command "Тест" "взять ключ")
  (handle-command "Тест" "take ключ")
  (handle-command "Тест" "t ключ")
  
  ;; 4. Инвентарь
  (handle-command "Тест" "инвентарь")
  (handle-command "Тест" "inventory")
  (handle-command "Тест" "i")
  
  ;; 5. Помощь
  (handle-command "Тест" "помощь")
  (handle-command "Тест" "help")
  (handle-command "Тест" "h")
  (handle-command "Тест" "?")
  
  ;; 6. Выход
  (handle-command "Тест" "выход")
  (handle-command "Тест" "exit")
  (handle-command "Тест" "quit")
  (handle-command "Тест" "q")
)
