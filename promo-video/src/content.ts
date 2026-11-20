/**
 * All on-screen text and the voice-over, scene by scene. Facts come from README.md, PLAN.md and
 * examples/plugins of the YiliGram repository.
 */
import {CONFIG} from './config';

export const TEXT = {
  intro: {
    title: 'YiliGram',
    subtitle: 'Telegram в стиле\nMaterial 3 Expressive',
  },
  expressive: {
    lines: ['Цвет из ваших обоев', 'Формы, которые оживают', 'Движение на пружинах'],
  },
  customization: {
    headline: 'Один слайдер —\nвесь интерфейс',
    chips: ['Цвета', 'Формы', 'Шрифты', 'Масштаб', 'Анимации', 'Вкладки', 'Наборы иконок', 'Меню', 'Пресеты'],
    presets: '7 пресетов',
  },
  plugins: {
    headline: 'Плагины\nна Python',
    points: ['Совместимы с exteraGram', 'Хуки Java-методов', 'Настройки плагинов', 'Отладка в VS Code', 'Safe mode'],
    /** From examples/plugins/hook_titles.plugin. */
    code: [
      'class TitleHook(MethodHook):',
      '    def before_hooked_method(self, param):',
      '        title = param.args[0]',
      '        mark = self.plugin.get_setting("mark", "✦")',
      '        param.args[0] = f"{title} {mark}"',
      '',
      'class TitleDecorator(BasePlugin):',
      '    def on_plugin_load(self):',
      '        cls = find_class("org.telegram.ui.ActionBar.ActionBar")',
      '        method = cls.getClass().getDeclaredMethod(',
      '            "setTitle", CharSequence)',
      '        self.hook_method(method, TitleHook(self))',
    ],
  },
  details: {
    typing: 'Живой набор текста',
    typed: 'Привет! Это YiliGram',
    badges: 'Бейджи YiliGram',
    badgeNames: ['Команда', 'Бета-тестер', 'Официальный ресурс'],
  },
  push: {
    headline: 'Уведомления\nчерез UnifiedPush',
    sub: 'Без Firebase',
    notification: {title: 'YiliGram', text: 'Новое сообщение'},
  },
  openSource: {
    headline: 'Открытый код',
    license: 'GPL-3.0',
    sub: 'Основан на официальном\nTelegram для Android',
  },
  download: {
    headline: 'Где найти',
    telegramLabel: 'Telegram-канал',
    githubLabel: 'GitHub',
    apk: 'APK в Releases на GitHub',
  },
  disclaimer: 'Неофициальный клиент. Не связан с Telegram',
};

/** Voice-over lines (also used as subtitles). `at` is seconds from the start of the scene. */
export const VOICEOVER: Record<string, {at: number; text: string}[]> = {
  intro: [{at: 3.2, text: 'Это YiliGram — Telegram в стиле Material 3 Expressive.'}],
  expressive: [
    {at: 0.4, text: 'Цвета берутся из ваших обоев.'},
    {at: 3.6, text: 'Формы перетекают друг в друга, а каждое движение — на пружинах.'},
  ],
  customization: [
    {at: 0.5, text: 'Настройте всё: цвета, формы, шрифты, масштаб, анимации, вкладки и меню.'},
    {at: 8.5, text: 'Один слайдер меняет весь интерфейс. А семь пресетов — за одно касание.'},
  ],
  plugins: [
    {at: 0.4, text: 'Плагины на Python, совместимые с exteraGram.'},
    {at: 5.0, text: 'Хуки Java-методов, настройки, отладка в VS Code и безопасный режим.'},
  ],
  details: [
    {at: 0.3, text: 'Буквы оживают прямо при наборе.'},
    {at: 3.8, text: 'А у команды, тестеров и официальных каналов — свои бейджи.'},
  ],
  push: [{at: 0.4, text: 'Уведомления — через UnifiedPush, без Firebase.'}],
  openSource: [{at: 0.4, text: 'Код открыт под GPL-3.0 и основан на официальном Telegram для Android.'}],
  download: [{at: 0.4, text: 'Скачайте YiliGram на GitHub, а новости — в нашем Telegram-канале.'}],
  disclaimer: [],
};

export const LINKS = {
  telegram: `https://${CONFIG.links.telegram}`,
  github: `https://${CONFIG.links.github}`,
};
