(ns game.game.items
  "Система предметов игры.
   Управление свойствами предметов, их взаимодействием и STM состоянием."
  (:require [game.game.world :as world]))

;; БАЗА ДАННЫХ ПРЕДМЕТОВ (immutable данные)
(def items-db
  {
   ;; Ключи и доступы
   "ключ-карта" {
     :name "Ключ-карта"
     :description "Пластиковая карта с магнитной полосой. Имеет логотип лаборатории 'Sigma Labs'."
     :type :key
     :weight 0.1
     :value 50
     :properties {:material "plastic" :magnetic true}
     :usable-in [:hallway :archive]
     :usage-effect "Открывает электронные замки"
     :examination "На обратной стороне выгравирован серийный номер: SL-2023-47"
   }
   
   "провод" {
     :name "Медный провод"
     :description "Длинный медный провод с изоляцией красного цвета. Длина около 2 метров."
     :type :component
     :weight 0.5
     :value 10
     :properties {:material "copper" :insulated true :length 2}
     :usable-in [:promenade :hallway]
     :usage-effect "Используется для починки электроники"
     :examination "На изоляции есть маркировка '10 AWG'. Один конец оголен."
     :combine-with ["схема"]
   }
   
   "схема" {
     :name "Схема подключения"
     :description "Техническая схема с обозначением разъемов и контактов."
     :type :document
     :weight 0.2
     :value 25
     :properties {:pages 3 :language "technical"}
     :usable-in [:promenade]
     :usage-effect "Показывает правильное подключение проводов"
     :examination "На схеме подпись: 'Консоль управления, контактная группа B7-B12'"
     :combine-with ["провод"]
   }
   
   "микроскоп" {
     :name "Электронный микроскоп"
     :description "Современный микроскоп с цифровым дисплеем. Увеличение до 1000x."
     :type :tool
     :weight 5.0
     :value 500
     :properties {:digital true :magnification 1000}
     :usable-in [:start]
     :usage-effect "Позволяет исследовать микрообъекты"
     :examination "Под линзой находится слайд с надписью: '3XX7 (X = месяц основания лаборатории)'"
     :fixed true  ;; Нельзя взять в инвентарь
   }
   
   "лабораторный-журнал" {
     :name "Лабораторный журнал"
     :description "Толстая книга с записями экспериментов. Обложка кожаная."
     :type :document
     :weight 1.5
     :value 75
     :properties {:pages 250 :year 1997}
     :usable-in [:archive :start]
     :usage-effect "Содержит исторические записи и подсказки"
     :examination "На первой странице: 'Основано: октябрь 1997 года. Директор: д-р А. Волков.'"
     :readable "Записи за 12.10.1997: 'Эксперимент #47: Успех! Код доступа к серверной: 3107'"
   }
   
   "формулы" {
     :name "Лист с формулами"
     :description "Бумажный лист с математическими уравнениями и химическими формулами."
     :type :document
     :weight 0.1
     :value 5
     :properties {:paper "A4" :handwritten true}
     :usable-in [:archive]
     :usage-effect "Содержит научные данные"
     :examination "В углу мелким почерком: 'Код для ежедневного доступа в серверную: 3107'"
   }
   
   "консоль" {
     :name "Консоль управления"
     :description "Центральная панель управления лабораторией. Имеет множество кнопок и экранов."
     :type :device
     :weight 100.0
     :value 10000
     :properties {:powered false :screens 3}
     :usable-in [:promenade]
     :usage-effect "Контролирует системы лаборатории"
     :examination "На дисплее мигает сообщение: 'ОШИБКА: Нет связи с источником питания'"
     :fixed true
     :requires ["провод" "схема"]
   }
   
   "батарейка" {
     :name "Батарейка типа АА"
     :description "Щелочная батарейка. Напряжение 1.5V."
     :type :component
     :weight 0.05
     :value 2
     :properties {:type "AA" :voltage 1.5 :charge "full"}
     :usable-in [:start :hallway]
     :usage-effect "Источник питания для мелкой электроники"
     :examination "На корпусе надпись: 'Energizer, срок годности 2025'"
   }
   
   "отвертка" {
     :name "Крестовая отвертка"
     :description "Отвертка с намагниченным жалом. Рукоятка прорезиненная."
     :type :tool
     :weight 0.3
     :value 15
     :properties {:size "PH2" :magnetic true}
     :usable-in [:start :promenade :hallway]
     :usage-effect "Для разборки оборудования"
     :examination "На ручке гравировка: 'Собственность тех. отдела'"
   }
   })

;; STM СОСТОЯНИЕ ПРЕДМЕТОВ (изменяемые свойства)
(defonce items-state
  (ref {
    ;; Состояния предметов (использованы/сломаны/активны)
    :conditions {
      "ключ-карта" {:used false :charged true}
      "провод" {:connected false :insulation-good true}
      "консоль" {:repaired false :powered false}
      "батарейка" {:charge 100}
    }
    
    ;; Позиции предметов (синхронизируется с миром)
    :positions {}
    
    ;; Взаимодействия между предметами
    :combinations {}
    
    ;; История использования
    :usage-history {}
  }))

;; ФУНКЦИИ ДЛЯ РАБОТЫ С БАЗОЙ ПРЕДМЕТОВ
(defn get-item
  "Получить полное описание предмета"
  [item-name]
  (get items-db item-name))

(defn get-item-name
  "Получить имя предмета"
  [item-name]
  (get-in items-db [item-name :name]))

(defn get-item-description
  "Получить описание предмета"
  [item-name]
  (get-in items-db [item-name :description]))

(defn get-item-type
  "Получить тип предмета"
  [item-name]
  (get-in items-db [item-name :type]))

(defn item-exists?
  "Проверить существование предмета"
  [item-name]
  (contains? items-db item-name))

(defn get-item-weight
  "Получить вес предмета"
  [item-name]
  (get-in items-db [item-name :weight]))

(defn is-item-fixed?
  "Проверить, закреплен ли предмет (нельзя взять)"
  [item-name]
  (get-in items-db [item-name :fixed]))

(defn get-item-examination
  "Получить текст осмотра предмета"
  [item-name]
  (get-in items-db [item-name :examination]))

(defn get-item-readable
  "Получить текст для чтения (если предмет - документ)"
  [item-name]
  (get-in items-db [item-name :readable]))

;; ФУНКЦИИ ДЛЯ РАБОТЫ СО СОСТОЯНИЕМ ПРЕДМЕТОВ (STM)
(defn set-item-condition!
  "Установить состояние предмета"
  [item-name condition value]
  (dosync
    (alter items-state assoc-in [:conditions item-name condition] value)))

(defn get-item-condition
  "Получить состояние предмета"
  [item-name condition]
  (get-in @items-state [:conditions item-name condition]))

(defn update-item-position!
  "Обновить позицию предмета в мире"
  [item-name room-name]
  (dosync
    (alter items-state assoc-in [:positions item-name] room-name)))

(defn get-item-position
  "Получить текущую позицию предмета"
  [item-name]
  (get-in @items-state [:positions item-name]))

(defn mark-item-used!
  "Пометить предмет как использованный"
  [item-name player-name]
  (dosync
    (set-item-condition! item-name :used true)
    (set-item-condition! item-name :last-used-by player-name)
    (set-item-condition! item-name :last-used-time (System/currentTimeMillis))
    (alter items-state update-in [:usage-history item-name] 
           (fn [history] (conj (or history []) 
                               {:player player-name 
                                :time (System/currentTimeMillis)})))))

(defn can-use-item?
  "Проверить, можно ли использовать предмет в данной комнате"
  [item-name room-name]
  (let [usable-rooms (get-in items-db [item-name :usable-in])]
    (and usable-rooms (some #{room-name} usable-rooms))))

(defn get-required-items
  "Получить список предметов, необходимых для использования данного"
  [item-name]
  (get-in items-db [item-name :requires]))

(defn get-combine-items
  "Получить предметы, с которыми можно комбинировать данный"
  [item-name]
  (get-in items-db [item-name :combine-with]))

(defn can-combine?
  "Проверить, можно ли комбинировать два предмета"
  [item1 item2]
  (let [combine-list1 (get-combine-items item1)
        combine-list2 (get-combine-items item2)]
    (or (and combine-list1 (some #{item2} combine-list1))
        (and combine-list2 (some #{item1} combine-list2)))))

(defn record-combination!
  "Записать факт комбинации предметов"
  [item1 item2 player-name]
  (dosync
    (alter items-state update-in [:combinations [item1 item2]]
           (fn [comb] (conj (or comb []) 
                           {:player player-name 
                            :time (System/currentTimeMillis)})))))

;; ФУНКЦИИ ДЛЯ ИНТЕГРАЦИИ С МИРОМ
(defn item-in-room?
  "Проверить, находится ли предмет в комнате"
  [item-name room-name]
  (let [world-items (world/get-room-items room-name)]
    (contains? world-items item-name)))

(defn move-item-to-room!
  "Переместить предмет в комнату"
  [item-name room-name]
  (when (item-exists? item-name)
    (let [current-room (get-item-position item-name)]
      (when current-room
        (world/remove-item-from-room! current-room item-name))
      (world/add-item-to-room! room-name item-name)
      (update-item-position! item-name room-name))))

(defn initialize-items-in-world!
  "Инициализировать предметы в мире согласно настройкам"
  []
  (dosync
    ;; Распределяем предметы по комнатам
    (world/add-item-to-room! :start "ключ-карта")
    (update-item-position! "ключ-карта" :start)
    
    (world/add-item-to-room! :start "провод")
    (update-item-position! "провод" :start)
    
    (world/add-item-to-room! :start "микроскоп")
    (update-item-position! "микроскоп" :start)
    
    (world/add-item-to-room! :hallway "схема")
    (update-item-position! "схема" :hallway)
    
    (world/add-item-to-room! :archive "лабораторный-журнал")
    (update-item-position! "лабораторный-журнал" :archive)
    
    (world/add-item-to-room! :archive "формулы")
    (update-item-position! "формулы" :archive)
    
    (world/add-item-to-room! :promenade "консоль")
    (update-item-position! "консоль" :promenade)))

;; ФУНКЦИИ ДЛЯ ИГРОВОЙ ЛОГИКИ
(defn use-item!
  "Использовать предмет"
  [item-name player-name room-name]
  (when (and (item-exists? item-name)
             (can-use-item? item-name room-name))
    (mark-item-used! item-name player-name)
    (let [item-type (get-item-type item-name)]
      (case item-type
        :key {:message "Вы прикладываете ключ-карту к считывателю"}
        :component {:message "Вы используете компонент"}
        :document {:message "Вы изучаете документ"}
        :tool {:message "Вы используете инструмент"}
        :device {:message "Вы взаимодействуете с устройством"}
        {:message "Вы используете предмет"}))))

(defn examine-item!
  "Осмотреть предмет"
  [item-name player-name]
  (when (item-exists? item-name)
    (let [examination (get-item-examination item-name)
          readable (get-item-readable item-name)
          condition (get-in @items-state [:conditions item-name])]
      {:examination examination
       :readable readable
       :condition condition
       :used (get-item-condition item-name :used)})))

(defn get-available-items-in-room
  "Получить доступные для взятия предметы в комнате"
  [room-name]
  (let [all-items (world/get-room-items room-name)]
    (filter #(not (is-item-fixed? %)) all-items)))

;; ИНИЦИАЛИЗАЦИЯ
(defn init-items-system
  "Инициализировать систему предметов"
  []
  (println "[ITEMS] Система предметов инициализирована")
  (println "[ITEMS] Загружено предметов:" (count items-db))
  (initialize-items-in-world!)
  (println "[ITEMS] Предметы размещены в мире"))

;; Автоматическая инициализация
(init-items-system)
