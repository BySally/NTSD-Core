---
navigation:
    parent: epp_intro/epp_intro-index.md
    title: Беспроводной соединитель
    icon: expatternprovider:wireless_connect
categories:
- extended devices
item_ids:
- expatternprovider:wireless_connect
- expatternprovider:wireless_tool
---

# Беспроводное соединение МЭ-сети

<Row gap="20">
<BlockImage id="expatternprovider:wireless_connect" scale="6"></BlockImage>
<ItemImage id="expatternprovider:wireless_tool" scale="6"></ItemImage>
</Row>

Два соединителя заменяют участок плотного МЭ-кабеля: через них части сети обмениваются каналами. Стены между устройствами не мешают связи. Это не точка доступа для беспроводного терминала.

Оба устройства должны находиться **в одном измерении и в загруженных чанках**. В NTNH нет фиксированного предела расстояния, но дальняя связь требует много энергии. Для связи между измерениями нужен квантовый мост.

## Как связать пару

1. Поставьте два соединителя и подключите к ним нужные части сети.
2. Нажмите <ItemLink id="expatternprovider:wireless_tool" /> по первому соединителю, затем по второму.
3. Откройте устройство пустой рукой: в окне видны состояние, расход энергии, каналы и координаты удалённого конца.

Shift + ПКМ инструментом по воздуху очищает выбранную точку. Удалённый конец можно подсветить кнопкой в окне соединителя. Чтобы подключить занятой соединитель к другой точке, сначала отключите прежнюю линию в его окне.

Несвязанные соединители:

<GameScene zoom="5" background="transparent">
  <ImportStructure src="../structure/wireless_connector_off.snbt"></ImportStructure>
</GameScene>

Связанная пара:

<GameScene zoom="5" background="transparent">
  <ImportStructure src="../structure/wireless_connector_on.snbt"></ImportStructure>
</GameScene>

## Цвет и каналы

Соединители окрашиваются <ItemLink id="ae2:color_applicator" />. Цвет определяет совместимость с соседними кабелями: используйте одинаковые цвета либо прозрачный кабель.

<GameScene zoom="3" background="transparent" interactive={true}>
  <ImportStructure src="../structure/wireless_connector_setup.snbt"></ImportStructure>
</GameScene>

Пропускная способность одного соединения соответствует плотному кабелю — до 32 каналов при обычных настройках AE2. Соединитель передаёт существующие каналы, а не создаёт новые. Для нескольких направлений используйте <ItemLink id="expatternprovider:wireless_hub" />.

## Расход энергии в NTNH

Каждый конец линии потребляет **1 + 0,1 × d × ln(d² + 3) AE/т**, где d — расстояние в блоках. Узел платит сумму расходов своих линий. Например, при расстоянии 100 блоков расход составляет примерно 93 AE/т на каждом конце.

Карты энергосбережения не уменьшают этот расход. Несвязанные устройства не расходуют энергию. Если питание недостаточно, проверьте общую мощность сети и накопители; сокращение расстояния тоже заметно снижает нагрузку.
