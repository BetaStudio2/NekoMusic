/**
 * 图标数据注册表
 * ------------------------------------------------------------
 * morphicons 消费的是「图标数据」(IconNode / d 字符串)，不是图标组件。
 * 这里统一从 vanilla lucide 包按名导入，并建立 name → IconNode 映射，
 * 使页面层只需 <NIcon name="play" />，不直接耦合 lucide。
 *
 * 只在此文件 import lucide，新增图标时在此登记即可（保证 tree-shaking）。
 */

import {
  // 播放 / 媒体控制
  Play, Pause, SkipBack, SkipForward, Volume2, VolumeX, Volume1,
  Repeat, Repeat1, Shuffle, ListMusic, Music, Music2, Mic2, AudioLines,
  Rewind, FastForward, Radio, Disc3, Waves, CircleStop,
  // 通用操作
  Search, SearchX, X, Check, Plus, Minus, Trash2, Pencil, Copy, Share2, Download,
  Upload, Filter, SlidersHorizontal, RefreshCw, ExternalLink, Link, Link2,
  MoreHorizontal, MoreVertical, GripVertical, Maximize2, Minimize2, Expand,
  // 方向
  ChevronLeft, ChevronRight, ChevronUp, ChevronDown, ArrowLeft, ArrowRight,
  ArrowUp, ArrowDown, ArrowUpDown, CornerDownLeft,
  // 用户 / 账户
  User, UserPlus, Users, UserCircle, LogIn, LogOut, KeyRound, Lock, Mail,
  ShieldCheck, ShieldAlert, Bell, BellOff, Settings, UserCog,
  // 文件 / 内容
  FileText, FileMusic, FolderOpen, Folder, File, FileUp, Image, ImagePlus,
  ClipboardList, ClipboardCheck, BookOpen, ListChecks, Tags, Tag, Hash,
  // 状态 / 反馈
  Heart, Star, ThumbsUp, ThumbsDown, Flag, Info, TriangleAlert, CircleAlert,
  CircleCheck, CircleX, CircleHelp, Loader, LoaderCircle, Sparkles, Flame,
  HeartOff,
  Crown, Gem, BadgeCheck, Award, Trophy, TrendingUp, TrendingDown, ChartNoAxesColumn, Activity,
  // 主题 / 展示
  Sun, Moon, Monitor, Eye, EyeOff, Palette, LayoutGrid, LayoutList, Menu,
  PanelLeft, PanelRight, Table, Grid3x3, GalleryHorizontal, Rows3,
  Video, Clapperboard,
  // 时间 / 位置
  Clock, Calendar, CalendarDays, History, MapPin, Timer, Hourglass,
  // 其他
  Home, Headphones, HeadphoneOff, Headset, Speaker, Cast, Wifi, WifiOff,
  Smartphone, MonitorSmartphone, HardDrive, Cloud, CloudUpload, CloudDownload,
  Package,
  ScanLine, QrCode, Send, Inbox, MessageSquare, CirclePlay, CirclePause,
  Baby, Cat, Bug, Code, Terminal, Database, Server,
  Trash, Ban, Crosshair, Move, ZoomIn, ZoomOut, RotateCcw, RotateCw,
} from 'lucide'

/**
 * 图标名 → 图标数据。命名统一使用 kebab-case，贴近 lucide 官方命名。
 * @type {Record<string, import('morphicons/vue').IconInput>}
 */
export const icons = {
  // 播放 / 媒体
  play: Play,
  pause: Pause,
  'skip-back': SkipBack,
  'skip-forward': SkipForward,
  'circle-play': CirclePlay,
  'circle-pause': CirclePause,
  'circle-stop': CircleStop,
  rewind: Rewind,
  'fast-forward': FastForward,
  'volume-2': Volume2,
  'volume-1': Volume1,
  'volume-x': VolumeX,
  repeat: Repeat,
  'repeat-1': Repeat1,
  shuffle: Shuffle,
  'list-music': ListMusic,
  music: Music,
  'music-2': Music2,
  'mic-2': Mic2,
  'audio-lines': AudioLines,
  radio: Radio,
  'disc-3': Disc3,
  waves: Waves,
  headphones: Headphones,
  'headphone-off': HeadphoneOff,
  headset: Headset,
  speaker: Speaker,
  cast: Cast,

  // 通用操作
  search: Search,
  'search-x': SearchX,
  close: X,
  check: Check,
  plus: Plus,
  minus: Minus,
  trash: Trash,
  'trash-2': Trash2,
  edit: Pencil,
  copy: Copy,
  share: Share2,
  download: Download,
  upload: Upload,
  filter: Filter,
  sliders: SlidersHorizontal,
  refresh: RefreshCw,
  'external-link': ExternalLink,
  link: Link,
  'link-2': Link2,
  'more-h': MoreHorizontal,
  'more-v': MoreVertical,
  grip: GripVertical,
  maximize: Maximize2,
  minimize: Minimize2,
  expand: Expand,
  move: Move,
  ban: Ban,
  crosshair: Crosshair,
  'zoom-in': ZoomIn,
  'zoom-out': ZoomOut,
  'rotate-ccw': RotateCcw,
  'rotate-cw': RotateCw,

  // 方向
  'chevron-left': ChevronLeft,
  'chevron-right': ChevronRight,
  'chevron-up': ChevronUp,
  'chevron-down': ChevronDown,
  'arrow-left': ArrowLeft,
  'arrow-right': ArrowRight,
  'arrow-up': ArrowUp,
  'arrow-down': ArrowDown,
  'arrow-up-down': ArrowUpDown,
  'corner-down-left': CornerDownLeft,

  // 用户 / 账户
  user: User,
  'user-plus': UserPlus,
  users: Users,
  'user-circle': UserCircle,
  'user-cog': UserCog,
  login: LogIn,
  logout: LogOut,
  key: KeyRound,
  lock: Lock,
  mail: Mail,
  'shield-check': ShieldCheck,
  'shield-alert': ShieldAlert,
  bell: Bell,
  'bell-off': BellOff,
  settings: Settings,

  // 文件 / 内容
  'file-text': FileText,
  'file-music': FileMusic,
  'folder-open': FolderOpen,
  folder: Folder,
  file: File,
  'file-up': FileUp,
  image: Image,
  'image-plus': ImagePlus,
  clipboard: ClipboardList,
  'clipboard-check': ClipboardCheck,
  book: BookOpen,
  'list-checks': ListChecks,
  tags: Tags,
  tag: Tag,
  hash: Hash,

  // 状态 / 反馈
  heart: Heart,
  'heart-off': HeartOff,
  star: Star,
  'thumbs-up': ThumbsUp,
  'thumbs-down': ThumbsDown,
  flag: Flag,
  info: Info,
  warning: TriangleAlert,
  'circle-alert': CircleAlert,
  'circle-check': CircleCheck,
  'circle-x': CircleX,
  'circle-help': CircleHelp,
  loader: Loader,
  'loader-circle': LoaderCircle,
  sparkles: Sparkles,
  flame: Flame,
  crown: Crown,
  gem: Gem,
  'badge-check': BadgeCheck,
  award: Award,
  trophy: Trophy,
  'trending-up': TrendingUp,
  'trending-down': TrendingDown,
  activity: Activity,
  chart: ChartNoAxesColumn,

  // 主题 / 展示
  sun: Sun,
  moon: Moon,
  monitor: Monitor,
  eye: Eye,
  'eye-off': EyeOff,
  palette: Palette,
  'layout-grid': LayoutGrid,
  'layout-list': LayoutList,
  menu: Menu,
  'panel-left': PanelLeft,
  'panel-right': PanelRight,
  table: Table,
  grid: Grid3x3,
  gallery: GalleryHorizontal,
  rows: Rows3,
  video: Video,
  clapperboard: Clapperboard,

  // 时间 / 位置
  clock: Clock,
  calendar: Calendar,
  'calendar-days': CalendarDays,
  history: History,
  'map-pin': MapPin,
  timer: Timer,
  hourglass: Hourglass,

  // 其他
  home: Home,
  wifi: Wifi,
  'wifi-off': WifiOff,
  smartphone: Smartphone,
  'monitor-smartphone': MonitorSmartphone,
  'hard-drive': HardDrive,
  package: Package,
  cloud: Cloud,
  'cloud-upload': CloudUpload,
  'cloud-download': CloudDownload,
  'scan-line': ScanLine,
  qrcode: QrCode,
  send: Send,
  inbox: Inbox,
  message: MessageSquare,
  code: Code,
  terminal: Terminal,
  database: Database,
  server: Server,
  bug: Bug,
  cat: Cat,
  baby: Baby,
}

/**
 * 解析图标名 → 图标数据。
 * @param {string} name 图标名（kebab-case）
 * @returns {import('morphicons/vue').IconInput | undefined}
 */
export function resolveIcon(name) {
  if (!name) return undefined
  return icons[name]
}

export default icons
