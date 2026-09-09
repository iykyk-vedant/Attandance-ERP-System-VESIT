'use client'

import { useState } from 'react'
import {
  Bell,
  BookOpen,
  CalendarDays,
  Check,
  ChevronDown,
  ChevronRight,
  ClipboardList,
  Clock3,
  LayoutDashboard,
  Menu,
  MessageSquare,
  MoreHorizontal,
  Search,
  Settings,
  Sun,
  Moon,
  Users,
  X,
} from 'lucide-react'

const schedule = [
  { time: '09:00 – 10:00', code: 'ITC501', title: 'Advanced Database Management', room: 'Room 302', batch: 'TE IT A', accent: 'cyan' },
  { time: '10:15 – 11:15', code: 'ITL502', title: 'Machine Learning Lab', room: 'Lab 4', batch: 'TE IT B', accent: 'green' },
  { time: '12:00 – 13:00', code: 'ITC503', title: 'Web Technologies', room: 'Room 204', batch: 'TE IT A', accent: 'violet' },
  { time: '14:00 – 15:00', code: 'ITL504', title: 'Project Review', room: 'Seminar Hall', batch: 'BE IT', accent: 'amber' },
]

const navItems = [
  { label: 'Dashboard', icon: LayoutDashboard },
  { label: 'Attendance', icon: ClipboardList },
  { label: 'Course Diary', icon: BookOpen },
  { label: 'My Courses', icon: Users },
]

export default function Page() {
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [activeNav, setActiveNav] = useState('Dashboard')
  const [notice, setNotice] = useState('')
  const [theme, setTheme] = useState<'dark' | 'light'>('dark')

  const announce = (message: string) => {
    setNotice(message)
    window.setTimeout(() => setNotice(''), 2400)
  }

  return (
    <div className={`faculty-app theme-${theme}`}>
      <aside className={`faculty-sidebar ${sidebarOpen ? 'open' : ''}`}>
        <div className="faculty-brand">
          <div className="brand-symbol">OA</div>
          <div><strong>OpenAttend</strong><span>Faculty workspace</span></div>
        </div>
        <div className="faculty-profile">
          <div className="faculty-avatar">PS</div>
          <div><strong>Priya Shah</strong><span>Information Technology</span></div>
          <button aria-label="Open profile menu" onClick={() => announce('Profile menu opened')}><MoreHorizontal size={17} /></button>
        </div>
        <nav className="faculty-nav" aria-label="Faculty navigation">
          <span className="faculty-nav-label">Workspace</span>
          {navItems.map(({ label, icon: Icon }) => (
            <button key={label} className={activeNav === label ? 'faculty-nav-item active' : 'faculty-nav-item'} onClick={() => { setActiveNav(label); announce(`${label} opened`) }}>
              <Icon size={17} />{label}{label === 'Attendance' && <span className="nav-pill">3</span>}
            </button>
          ))}
          <span className="faculty-nav-label separate">Manage</span>
          <button className="faculty-nav-item" onClick={() => announce('Teaching desk opened')}><CalendarDays size={17} />Teaching Desk</button>
          <button className="faculty-nav-item" onClick={() => announce('Settings opened')}><Settings size={17} />Settings</button>
        </nav>
        <div className="sidebar-help"><MessageSquare size={16} /><div><strong>Need help?</strong><span>Open a support request</span></div><ChevronRight size={15} /></div>
      </aside>

      <div className="faculty-main">
        <header className="faculty-topbar">
          <button className="mobile-menu" aria-label="Toggle navigation" onClick={() => setSidebarOpen(!sidebarOpen)}><Menu size={20} /></button>
          <div className="topbar-context"><span>VESIT / FACULTY</span><strong>{activeNav}</strong></div>
          <div className="topbar-actions"><button aria-label="Search" onClick={() => announce('Search is ready')}><Search size={18} /></button><button className="theme-toggle" aria-label={`Switch to ${theme === 'dark' ? 'light' : 'dark'} theme`} onClick={() => { const nextTheme = theme === 'dark' ? 'light' : 'dark'; setTheme(nextTheme); announce(`${nextTheme === 'light' ? 'Light' : 'Dark'} theme enabled`) }}>{theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}</button><button className="has-notification" aria-label="Notifications" onClick={() => announce('You have 3 new notifications')}><Bell size={18} /><i /></button><div className="top-avatar">PS</div></div>
        </header>

        <main className="faculty-content">
          <section className="faculty-hero">
            <div><p className="faculty-kicker">MONDAY, 08 SEPTEMBER 2026</p><h1>Good morning, Priya.</h1><p>Here&apos;s your teaching overview for today.</p></div>
            <button className="date-control" onClick={() => announce('Date picker opened')}><CalendarDays size={16} />Today <ChevronDown size={15} /></button>
          </section>

          <section className="overview-grid" aria-label="Faculty overview">
            <article className="overview-card cyan"><div className="overview-card-head"><span>Classes today</span><Clock3 size={17} /></div><strong>04</strong><p>2 completed <span>·</span> 2 upcoming</p></article>
            <article className="overview-card green"><div className="overview-card-head"><span>Students tracked</span><Users size={17} /></div><strong>122</strong><p><em>+8%</em> from last month</p></article>
            <article className="overview-card violet"><div className="overview-card-head"><span>Average attendance</span><ClipboardList size={17} /></div><strong>81.7%</strong><p><em>+1.4%</em> since August</p></article>
            <article className="overview-card amber"><div className="overview-card-head"><span>Pending actions</span><Bell size={17} /></div><strong>07</strong><p>Need your attention</p></article>
          </section>

          <div className="faculty-columns">
            <section className="schedule-panel">
              <div className="section-heading"><div><p className="faculty-kicker">YOUR DAY</p><h2>Today&apos;s schedule</h2></div><button onClick={() => announce('Full calendar opened')}>View calendar <ChevronRight size={15} /></button></div>
              <div className="schedule-list">{schedule.map((item, index) => <article className={`schedule-item ${index === 0 ? 'current' : ''}`} key={item.code}><div className="schedule-time">{item.time}</div><div className={`schedule-dot ${item.accent}`} /><div className="schedule-details"><div><span className="course-code">{item.code}</span><span className="batch-label">{item.batch}</span></div><h3>{item.title}</h3><p>{item.room}</p></div><button className="schedule-more" aria-label={`More options for ${item.title}`} onClick={() => announce(`${item.title} options opened`)}><MoreHorizontal size={17} /></button></article>)}</div>
            </section>
            <aside className="focus-panel"><div className="section-heading"><div><p className="faculty-kicker">QUICK ACCESS</p><h2>Teaching desk</h2></div><MoreHorizontal size={17} /></div><button className="focus-card" onClick={() => announce('Attendance entry opened')}><span className="focus-icon cyan"><ClipboardList size={18} /></span><span><strong>Mark attendance</strong><small>2 classes waiting</small></span><ChevronRight size={16} /></button><button className="focus-card" onClick={() => announce('Course diary opened')}><span className="focus-icon green"><BookOpen size={18} /></span><span><strong>Update course diary</strong><small>3 entries due today</small></span><ChevronRight size={16} /></button><button className="focus-card" onClick={() => announce('Student messages opened')}><span className="focus-icon violet"><MessageSquare size={18} /></span><span><strong>Student messages</strong><small>4 unread messages</small></span><ChevronRight size={16} /></button><div className="focus-note"><span className="note-line" /><div><strong>Semester 1</strong><span>Week 06 of 18</span></div><div className="semester-progress"><i /></div></div></aside>
          </div>

          <section className="notice-strip"><div className="notice-symbol"><Bell size={17} /></div><div><strong>Attendance entries are due</strong><span>Remember to submit today&apos;s class records before 6:00 PM.</span></div><button onClick={() => announce('Attendance workflow opened')}>Review now <ChevronRight size={15} /></button></section>
          <footer className="faculty-footer"><span>OpenAttend · VESIT Faculty Workspace</span><span><span className="online-dot" /> All systems operational</span></footer>
        </main>
      </div>
      {notice && <div className="faculty-toast" role="status"><Check size={16} />{notice}<button aria-label="Dismiss notification" onClick={() => setNotice('')}><X size={14} /></button></div>}
    </div>
  )
}
