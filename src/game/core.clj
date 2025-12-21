(ns game.core
  "Главная точка входа игры 'Побег из лаборатории'.
   Координирует все системы: STM мир, игроков, предметы."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))



;; ГЛОБАЛЬНЫЕ ПЕРЕМЕННЫЕ

(defonce game-state
  (atom {
    :initialized false
    :running false
    :start-time nil
    :active-players 0
    :max-players 4
    :mode :cooperative
    :log []
  }))

(defonce world-ref (ref {}))
(defonce players-ref (ref {}))
(defonce items-ref (ref {}))



;; БАЗОВЫЕ ФУНКЦИИ STM

(defn get-world []
  @world-ref)

(defn get-players []
  @players-ref)

(defn get-items []
  @items-ref)

(defn add-log-entry [entry]
  (swap! game-state update :log conj {:time (System/currentTimeMillis)
                                      :entry entry}))



;; ЗАГРУЗКА РЕСУРСОВ

(defn load-edn-file [path]
  "Загрузить EDN файл"
  (try
    (when (.exists (io/file path))
      (with-open [r (io/reader path)]
        (edn/read (java.io.PushbackReader. r))))
    (catch Exception e
      (println "[ERROR] Ошибка загрузки файла" path ":" e)
      nil)))

(defn load-russian-rooms []
  "Загрузить русские комнаты из resources/rooms_ru/"
  (println "[CORE] Загрузка русских комнат...")
  
  (let [room-files ["laboratory.edn" "hallway_ru.edn" "archive.edn" 
                    "console_room.edn" "exit.edn"]
        rooms-loaded (atom {})]
    
    (doseq [room-file room-files]
      (let [path (str "resources/rooms_ru/" room-file)
            room-data (load-edn-file path)]
        
        (when room-data
          (let [room-key (keyword (str/replace room-file #"\.edn$" ""))]
            (swap! rooms-loaded assoc room-key room-data)
            (println "  ✓ Загружена:" (:name room-data))))))
    
    ;; Сохраняем в STM
    (dosync
      (ref-set world-ref (assoc @world-ref :rooms @rooms-loaded)))
    
    (println "  Всего комнат:" (count @rooms-loaded))
    @rooms-loaded))

(defn load-items []
  "Загрузить предметы из resources/items/"
  (println "[CORE] Загрузка предметов...")
  
  (let [item-files ["keycard.edn" "microscope.edn" "wire.edn" 
                    "journal.edn" "blueprint.edn" "formulas.edn" "console.edn"]
        items-loaded (atom {})]
    
    (doseq [item-file item-files]
      (let [path (str "resources/items/" item-file)
            item-data (load-edn-file path)]
        
        (when item-data
          (let [item-key (keyword (str/replace item-file #"\.edn$" ""))]
            (swap! items-loaded assoc item-key item-data)
            (println "  ✓ Загружен:" (:name item-data))))))
    
    ;; Сохраняем в STM
    (dosync
      (ref-set items-ref @items-loaded))
    
    (println "  Всего предметов:" (count @items-loaded))
    @items-loaded))



;; УПРАВЛЕНИЕ КОМНАТАМИ

(defn get-room [room-key]
  "Получить комнату по ключу"
  (get-in @world-ref [:rooms room-key]))

(defn get-all-rooms []
  "Получить все комнаты"
  (keys (get-in @world-ref [:rooms])))

(defn add-room! [room-key room-data]
  "Добавить комнату"
  (dosync
    (alter world-ref assoc-in [:rooms room-key] room-data)))

(defn update-room! [room-key f & args]
  "Обновить комнату"
  (dosync
    (apply alter world-ref update-in [:rooms room-key] f args)))



;; УПРАВЛЕНИЕ ИГРОКАМИ

(defn add-player! [player-name]
  "Добавить игрока"
  (dosync
    (alter players-ref assoc player-name {
      :name player-name
      :room :laboratory
      :inventory #{}
      :joined-at (System/currentTimeMillis)
      :last-action (System/currentTimeMillis)
      :stats {
        :commands-executed 0
        :items-taken 0
        :rooms-visited #{:laboratory}
      }
    })
    
    ;; Добавляем игрока в стартовую комнату
    (alter world-ref update-in [:rooms :laboratory :players] conj player-name)
    
    (add-log-entry (str "Игрок подключился: " player-name))
    {:success true
     :player player-name
     :room :laboratory}))

(defn get-player [player-name]
  "Получить игрока"
  (get @players-ref player-name))

(defn get-player-room [player-name]
  "Получить комнату игрока"
  (:room (get-player player-name)))

(defn set-player-room! [player-name room-key]
  "Установить комнату игрока"
  (dosync
    (let [old-room (get-player-room player-name)
          room-data (get-room room-key)]
      
      (when (and old-room room-data)
        ;; Убираем из старой комнаты
        (alter world-ref update-in [:rooms old-room :players] disj player-name)
        
        ;; Добавляем в новую комнату
        (alter world-ref update-in [:rooms room-key :players] conj player-name)
        
        ;; Обновляем у игрока
        (alter players-ref assoc-in [player-name :room] room-key)
        
        ;; Обновляем статистику
        (alter players-ref update-in [player-name :stats :rooms-visited] conj room-key)
        
        (add-log-entry (str player-name " переместился из " old-room " в " room-key))
        true))))

(defn get-players-in-room [room-key]
  "Получить игроков в комнате"
  (get-in @world-ref [:rooms room-key :players]))

(defn move-player! [player-name direction]
  "Переместить игрока"
  (let [current-room (get-player-room player-name)
        room-data (get-room current-room)
        exits (:exits room-data)
        target-room (get exits (keyword direction))]
    
    (if target-room
      (do
        (set-player-room! player-name target-room)
        {:success true
         :from current-room
         :to target-room
         :message (str "Вы переместились в " (:name (get-room target-room)))})
      {:error true
       :message "Нельзя пойти в этом направлении"})))



;; УПРАВЛЕНИЕ ПРЕДМЕТАМИ

(defn get-item [item-key]
  "Получить предмет"
  (get @items-ref item-key))

(defn get-item-display-name [item-key]
  "Получить отображаемое имя предмета"
  (:name (get-item item-key)))

(defn player-has-item? [player-name item-key]
  "Проверить, есть ли у игрока предмет"
  (contains? (get-in @players-ref [player-name :inventory]) item-key))

(defn add-to-inventory! [player-name item-key]
  "Добавить предмет в инвентарь"
  (dosync
    (alter players-ref update-in [player-name :inventory] conj item-key)
    (alter players-ref update-in [player-name :stats :items-taken] inc)
    
    (add-log-entry (str player-name " взял " (get-item-display-name item-key)))
    true))

(defn remove-from-inventory! [player-name item-key]
  "Удалить предмет из инвентаря"
  (dosync
    (alter players-ref update-in [player-name :inventory] disj item-key)
    
    (add-log-entry (str player-name " положил " (get-item-display-name item-key)))
    true))

(defn get-player-inventory [player-name]
  "Получить инвентарь игрока"
  (get-in @players-ref [player-name :inventory]))




;; КОМАНДЫ ИГРЫ

(defn handle-look [player-name]
  "Обработать команду 'осмотреть'"
  (let [room-key (get-player-room player-name)
        room-data (get-room room-key)]
    
    (when room-data
      (add-log-entry (str player-name " осмотрел комнату " room-key))
      
      {:type :look
       :room (:name room-data)
       :description (:desc room-data)
       :items (map get-item-display-name (:items room-data))
       :players (vec (disj (get-players-in-room room-key) player-name))
       :exits (keys (:exits room-data))})))

(defn handle-take [player-name item-pattern]
  "Взять предмет"
  (let [room-key (get-player-room player-name)
        room-data (get-room room-key)
        room-items (:items room-data)
        
        ;; Ищем предмет по паттерну
        matching-item (first (filter #(str/includes? 
                                       (str/lower-case (get-item-display-name %)) 
                                       (str/lower-case item-pattern)) 
                                     room-items))]
    
    (if matching-item
      (dosync
        ;; Убираем из комнаты
        (alter world-ref update-in [:rooms room-key :items] disj matching-item)
        
        ;; Добавляем в инвентарь
        (add-to-inventory! player-name matching-item)
        
        {:success true
         :message (str "Вы взяли: " (get-item-display-name matching-item))
         :item matching-item})
      
      {:error true
       :message "Такого предмета здесь нет"})))

(defn handle-drop [player-name item-pattern]
  "Положить предмет"
  (let [inventory (get-player-inventory player-name)
        
        ;; Ищем предмет в инвентаре
        matching-item (first (filter #(str/includes? 
                                       (str/lower-case (get-item-display-name %)) 
                                       (str/lower-case item-pattern)) 
                                     inventory))]
    
    (if matching-item
      (dosync
        (let [room-key (get-player-room player-name)]
          
          ;; Убираем из инвентаря
          (remove-from-inventory! player-name matching-item)
          
          ;; Добавляем в комнату
          (alter world-ref update-in [:rooms room-key :items] conj matching-item)
          
          {:success true
           :message (str "Вы положили: " (get-item-display-name matching-item))
           :item matching-item}))
      
      {:error true
       :message "У вас нет такого предмета"})))

(defn handle-inventory [player-name]
  "Показать инвентарь"
  (let [inventory (get-player-inventory player-name)]
    {:type :inventory
     :player player-name
     :items (map get-item-display-name inventory)
     :count (count inventory)}))

(defn handle-move [player-name direction]
  "Переместиться"
  (move-player! player-name direction))

(defn handle-say [player-name message]
  "Сказать что-то в комнате"
  (let [room-key (get-player-room player-name)
        other-players (disj (get-players-in-room room-key) player-name)]
    
    (add-log-entry (str player-name " сказал: \"" message "\""))
    
    {:type :say
     :from player-name
     :message message
     :room room-key
     :to-players (vec other-players)}))

(defn handle-help [player-name]
  "Показать справку"
  {:type :help
   :commands [
     "север, юг, запад, восток - перемещение"
     "look или осмотреть - осмотреть комнату"
     "взять [предмет] - взять предмет"
     "положить [предмет] - положить предмет"
     "инвентарь - показать инвентарь"
     "сказать [текст] - сказать что-то"
     "помощь - эта справка"
     "статус - ваш статус"
   ]})

(defn handle-status [player-name]
  "Показать статус"
  (let [player (get-player player-name)
        play-time (/ (- (System/currentTimeMillis) (:joined-at player)) 1000.0)
        stats (:stats player)]
    
    {:type :status
     :player player-name
     :room (:name (get-room (:room player)))
     :play-time (format "%.1f сек" play-time)
     :commands (:commands-executed stats 0)
     :items (:items-taken stats 0)
     :rooms-visited (count (:rooms-visited stats))
     :inventory-count (count (:inventory player))}))

(defn handle-command [player-name input]
  "Обработать команду игрока"
  (dosync
    (try
      ;; Обновляем статистику команд
      (alter players-ref update-in [player-name :stats :commands-executed] inc)
      (alter players-ref assoc-in [player-name :last-action] (System/currentTimeMillis))
      
      ;; Парсим команду
      (let [parts (str/split input #" ")
            command (str/lower-case (first parts))
            args (str/join " " (rest parts))]
        
        (cond
          ;; Перемещение
          (#{"север" "юг" "запад" "восток" "north" "south" "west" "east"} command)
          (handle-move player-name command)
          
          ;; Осмотр
          (#{"look" "осмотреть" "посмотреть"} command)
          (handle-look player-name)
          
          ;; Взять предмет
          (#{"взять" "take" "поднять"} command)
          (handle-take player-name args)
          
          ;; Положить предмет
          (#{"положить" "drop" "бросить"} command)
          (handle-drop player-name args)
          
          ;; Инвентарь
          (#{"инвентарь" "inventory" "инв"} command)
          (handle-inventory player-name)
          
          ;; Сказать
          (#{"сказать" "say" "говорить"} command)
          (handle-say player-name args)
          
          ;; Помощь
          (#{"помощь" "help" "справка"} command)
          (handle-help player-name)
          
          ;; Статус
          (#{"статус" "status"} command)
          (handle-status player-name)
          
          ;; Неизвестная команда
          :else
          {:error true
           :message "Неизвестная команда. Введите 'помощь' для списка команд."}))
      
      (catch Exception e
        (println "[ERROR] Ошибка в команде:" input e)
        {:error true
         :message "Ошибка выполнения команды"}))))



;; УПРАВЛЕНИЕ ИГРОЙ

(defn init-game []
  "Инициализировать игру"
  (println "ИНИЦИАЛИЗАЦИЯ ИГРЫ 'ПОБЕГ ИЗ ЛАБОРАТОРИИ'")
  
  ;; Загружаем ресурсы
  (load-russian-rooms)
  (load-items)
  
  ;; Устанавливаем флаги
  (swap! game-state assoc 
         :initialized true
         :start-time (System/currentTimeMillis)
         :running true)
  
  (println "Игра инициализирована")
  (println "   Комнат:" (count (get-all-rooms)))
  (println "   Предметов:" (count (keys @items-ref)))
  true)

(defn start-game []
  "Запустить игру"
  (if (:running @game-state)
    (do
      (println "[WARN] Игра уже запущена")
      false)
    (do
      (init-game)
      true)))

(defn stop-game []
  "Остановить игру"
  (swap! game-state assoc :running false)
  (println "Игра остановлена!")
  true)

(defn get-game-info []
  "Получить информацию об игре"
  (let [state @game-state]
    {:status (if (:running state) "запущена" "остановлена")
     :uptime (if (:start-time state)
               (format "%.1f сек" (/ (- (System/currentTimeMillis) (:start-time state)) 1000.0))
               "0 сек")
     :players (:active-players state)
     :max-players (:max-players state)
     :mode (:mode state)
     :log-entries (count (:log state))}))



;; ТЕСТОВЫЕ ФУНКЦИИ

(defn test-game []
  "Тестирование игры"
  (println "\nТЕСТИРОВАНИЕ ИГРЫ")
  
  (start-game)
  
  ;; Тест 1: Добавление игрока
  (println "\n1. Добавление игрока:")
  (println (add-player! "ТестовыйИгрок"))
  
  ;; Тест 2: Осмотр комнаты
  (println "\n2. Осмотр комнаты:")
  (println (handle-look "ТестовыйИгрок"))
  
  ;; Тест 3: Взятие предмета (если есть в комнате)
  (println "\n3. Взятие предмета:")
  (let [room (get-room :laboratory)
        items (:items room)]
    (when (seq items)
      (let [item (first items)]
        (println (handle-take "ТестовыйИгрок" (get-item-display-name item))))))
  
  ;; Тест 4: Инвентарь
  (println "\n4. Показать инвентарь:")
  (println (handle-inventory "ТестовыйИгрок"))
  
  ;; Тест 5: Статус
  (println "\n5. Статус игрока:")
  (println (handle-status "ТестовыйИгрок"))
  
  ;; Тест 6: Информация об игре
  (println "\n6. Информация об игре:")
  (println (get-game-info))
  
  (println "\nТестирование завершено"))




;; ТОЧКА ВХОДА

(defn -main
  "Главная функция запуска игры"
  [& args]
  (println "ЗАПУСК STM ИГРЫ НА CLOJURE")
  
  ;; Проверяем аргументы
  (if (some #{"--test"} args)
    (test-game)
    (do
      (start-game)
      
      (println "\nИгра запущена и готова к подключению!")
      (println "   Используйте --test для тестового режима")
      (println "\nИгра работает... (Ctrl+C для выхода)")
      
      ;; Основной цикл
      (while (:running @game-state)
        (Thread/sleep 1000)
        ;; Можно добавить периодические задачи здесь
        
        )))
  
  (println "\nЗавершение работы игры"))

;; Автоматическая инициализация при загрузке
(println "[CORE] Модуль core.clj загружен")

;; Для работы в REPL
(comment
  ;; Запустить игру
  (start-game)
  
  ;; Добавить игрока
  (add-player! "Алексей")
  
  ;; Выполнить команды
  (handle-command "Алексей" "осмотреть")
  (handle-command "Алексей" "взять ключ-карта")
  (handle-command "Алексей" "инвентарь")
  (handle-command "Алексей" "помощь")
  
  ;; Получить информацию
  (get-game-info)
  
  ;; Остановить игру
  (stop-game)
  
  ;; Запустить тесты
  (test-game)
)
