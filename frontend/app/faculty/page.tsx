'use client'

import { useState } from 'react'
import {
  Bell,
  BookOpen,
  CalendarDays,
  Check,
  ChevronRight,
  ClipboardCheck,
  Clock3,
  Cloud,
  FileText,
  GraduationCap,
  LayoutDashboard,
  Menu,
  Moon,
  MoreHorizontal,
  PencilLine,
  Plus,
  RefreshCw,
  Search,
  Settings,
  Sun,
  Users,
  X,
  Zap,
} from 'lucide-react'

const navItems = [
  { label: 'Dashboard', icon: LayoutDashboard },
  { label: 'Attendance', icon: ClipboardCheck },
  { label: 'Course Diary', icon: FileText },
  { label: 'My Courses', icon: BookOpen },
]

const schedules = [
  {
    time: '09:00 - 10:00 AM',
    title: 'Operating Systems',
    type: 'Theory',
    division: 'D12B',
    students: '68 Students',
    room: 'Room 408',
    status: 'Submitted at 09:56 AM',
    detail: '64 Present, 4 Absent',
    tone: 'done',
  },
  {
    time: '11:15 - 01:15 PM',
    title: 'OS Lab',
    type: 'Practical',
    division: 'D12B - Batch B2',
    students: '23 Students',
    room: 'Lab 204',
    status: 'Ready to Mark',
    detail: 'Attendance is open for this session',
    tone: 'ready',
  },
  {
    time: '02:30 - 03:30 PM',
    title: 'Cloud Systems',
    type: 'Elective',
    division: 'D12A & D12B',
    students: '72 Students',
    room: 'Room 310',
    status: 'Scheduled',
    detail: 'Today, 02:30 PM',
    tone: 'scheduled',
  },
]

const courses = [
  { code: 'CE401', title: 'Operating Systems', batch: 'D12B · Theory', sessions: '18', average: '84.2%', icon: ClipboardCheck },
  { code: 'CE401L', title: 'OS Lab', batch: 'D12B · Batches B1, B2', sessions: '12', average: '89.6%', icon: Settings },
  { code: 'CE451', title: 'Cloud Systems', batch: 'D12A & D12B · Elective', sessions: '8', average: '77.1%', icon: Cloud },
]

const revisions = [
  { title: 'Operating Systems', meta: 'D12B · Today, 09:00 AM', time: '21h 45m left', count: '64 Present · 4 Absent' },
  { title: 'Data Structures', meta: 'D12A · Yesterday, 02:30 PM', time: '46h 12m left', count: '58 Present · 6 Absent' },
]

export default function Page() {
  const [dark, setDark] = useState(false)
  const [activeNav, setActiveNav] = useState('Dashboard')
  const [menuOpen, setMenuOpen] = useState(false)
  const [marked, setMarked] = useState(false)
  const [notice, setNotice] = useState('')

  const notify = (message: string) => {
    setNotice(message)
    window.setTimeout(() => setNotice(''), 3000)
  }

  return (
    <div className={dark ? 'app-shell dark' : 'app-shell'}>
      <aside className="sidebar">
        <div className="brand"><span className="brand-mark"><GraduationCap size={19} /></span><span>OpenAttend</span><span className="brand-campus">VESIT</span></div>
        <div className="sidebar-label">Workspace</div>
        <nav aria-label="Main navigation">
          {navItems.map(({ label, icon: Icon }) => (
            <button key={label} className={`nav-item ${activeNav === label ? 'active' : ''}`} onClick={() => { setActiveNav(label); notify(`${label} view selected`) }}>
              <Icon size={18} strokeWidth={1.8} /><span>{label}</span>{label === 'Attendance' && <span className="nav-count">2</span>}
            </button>
          ))}
        </nav>
        <div className="sidebar-label sidebar-label-lower">Tools</div>
        <button className="nav-item" onClick={() => notify('Revision Desk opened')}><RefreshCw size={18} strokeWidth={1.8} /><span>Revision Desk</span></button>
        <button className="nav-item" onClick={() => notify('Settings opened')}><Settings size={18} strokeWidth={1.8} /><span>Settings</span></button>
        <div className="sidebar-bottom"><div className="faculty-mini"><span className="avatar">SS</span><span><strong>Sneha Sharma</strong><small>Faculty account</small></span><MoreHorizontal size={17} /></div><div className="campus-note"><span className="online-dot" /> VESIT · Academic Year 2024-25</div></div>
      </aside>

      <main className="main-content">
        <header className="topbar">
          <button className="mobile-menu" aria-label="Open navigation" onClick={() => setMenuOpen(!menuOpen)}><Menu size={21} /></button>
          <div className="breadcrumb"><span>Faculty workspace</span><ChevronRight size={14} /><strong>{activeNav}</strong></div>
          <div className="top-actions"><button className="icon-button" aria-label="Search" onClick={() => notify('Search is ready')}><Search size={18} /></button><button className="icon-button notification" aria-label="Notifications" onClick={() => notify('No new notifications')}><Bell size={18} /><i /></button><button className="theme-toggle" aria-label="Toggle color theme" onClick={() => setDark(!dark)}>{dark ? <Sun size={17} /> : <Moon size={17} />}</button><span className="top-avatar">SS</span></div>
        </header>
        {menuOpen && <div className="mobile-nav">{navItems.map(({ label, icon: Icon }) => <button key={label} className={activeNav === label ? 'active' : ''} onClick={() => { setActiveNav(label); setMenuOpen(false) }}><Icon size={17} /> {label}</button>)}</div>}

        <div className="page-wrap">
          <section className="welcome-row"><div><p className="eyebrow">MONDAY, 19 AUGUST 2024 <span className="live-pill"><span className="online-dot" /> LIVE</span></p><h1>Good morning, Prof. Sneha.</h1><p className="subtitle">Your classroom is ready. Here&apos;s what&apos;s on your teaching desk today.</p></div><button className="date-button" onClick={() => notify('Calendar opened')}><CalendarDays size={17} /> <span>Today, Aug 19</span><ChevronRight size={15} /></button></section>

          <section className="metrics-grid" aria-label="Faculty metrics">
            <Metric icon={<CalendarDays />} label="Today&apos;s Lectures" value="3" note="1 done · 2 pending" accent="pine" />
            <Metric icon={<Users />} label="Avg Student Attendance" value="83.6%" note="Across my classes" accent="blue" />
            <Metric icon={<Clock3 />} label="Active 48h Edit Windows" value="2" note="Editable without HOD unlock" accent="amber" />
            <Metric icon={<BookOpen />} label="NAAC Syllabus Covered" value="26/45" note="Hours logged · 57%" accent="violet" progress />
          </section>

          <section className="section-heading"><div><p className="eyebrow">TEACHING DESK</p><h2>Today&apos;s teaching schedule</h2></div><button className="text-button" onClick={() => notify('Full timetable opened')}>View full timetable <ChevronRight size={15} /></button></section>
          <section className="schedule-list">
            {schedules.map((item, index) => <ScheduleCard key={item.title} item={item} index={index} marked={marked} onAction={(action) => action === 'mark' ? setMarked(true) : notify(action)} />)}
          </section>

          <div className="lower-grid">
            <section><div className="section-heading compact"><div><p className="eyebrow">YOUR TEACHING LOAD</p><h2>Assigned courses</h2></div><button className="small-icon-button" aria-label="Add course" onClick={() => notify('Course allocation request started')}><Plus size={17} /></button></div><div className="course-grid">{courses.map((course) => <article className="course-card" key={course.code}><div className="course-icon"><course.icon size={18} /></div><div className="course-top"><span className="code-chip">{course.code}</span><button aria-label={`More options for ${course.title}`} onClick={() => notify('Course options opened')}><MoreHorizontal size={17} /></button></div><h3>{course.title}</h3><p>{course.batch}</p><div className="course-stats"><span><b>{course.sessions}</b><small>Sessions taken</small></span><span><b>{course.average}</b><small>Class average</small></span></div><button className="outline-button" onClick={() => notify(`${course.title} register opened`)}>View full register <ChevronRight size={14} /></button></article>)}</div></section>
            <section><div className="section-heading compact"><div><p className="eyebrow">COMPLIANCE & CONTROL</p><h2>48-hour revision desk</h2></div><button className="text-button" onClick={() => notify('All revisions opened')}>View all <ChevronRight size={15} /></button></div><div className="revision-list">{revisions.map((revision) => <article className="revision-card" key={revision.title}><div className="revision-header"><span className="revision-icon"><PencilLine size={16} /></span><span className="revision-time"><Clock3 size={13} /> {revision.time}</span></div><h3>{revision.title}</h3><p>{revision.meta}</p><div className="revision-footer"><span>{revision.count}</span><button onClick={() => notify(`Editing ${revision.title} attendance`)}>Adjust attendance <ChevronRight size={14} /></button></div></article>)}</div></section>
          </div>
          <footer><span><span className="online-dot" /> All systems operational</span><span>NAAC Academic Course Diary <span className="footer-separator">·</span> <button onClick={() => notify('Help center opened')}>Need help?</button></span></footer>
        </div>
      </main>
      {notice && <div className="toast" role="status"><Check size={16} /> {notice}<button aria-label="Dismiss notification" onClick={() => setNotice('')}><X size={14} /></button></div>}
    </div>
  )
}

function Metric({ icon, label, value, note, accent, progress }: { icon: React.ReactNode; label: string; value: string; note: string; accent: string; progress?: boolean }) {
  return <article className={`metric-card ${accent}`}><div className="metric-icon">{icon}</div><div className="metric-label">{label}</div><strong>{value}</strong><p>{note}</p>{progress && <div className="progress-track"><span style={{ width: '57%' }} /></div>}</article>
}

function ScheduleCard({ item, index, marked, onAction }: { item: typeof schedules[number]; index: number; marked: boolean; onAction: (action: string) => void }) {
  return <article className={`schedule-card ${item.tone}`}><div className="slot-number">0{index + 1}</div><div className="schedule-time"><span>{item.time}</span><small>{item.room}</small></div><div className="schedule-info"><div className="title-line"><h3>{item.title}</h3><span className="type-chip">{item.type}</span></div><p>{item.division} <span className="dot-separator">·</span> {item.students}</p><div className={`status-line ${item.tone}`}><span className="status-dot" /> <strong>{marked && item.tone === 'ready' ? 'Submitted just now' : item.status}</strong><span className="status-detail">{marked && item.tone === 'ready' ? '21 Present, 2 Absent' : item.detail}</span></div></div><div className="schedule-action">{item.tone === 'done' && <button className="outline-button" onClick={() => onAction('Attendance edit window opened')}>Edit attendance <span className="mono">46h left</span></button>}{item.tone === 'ready' && <button className="primary-action" onClick={() => onAction('mark')} disabled={marked}>{marked ? <><Check size={16} /> Attendance marked</> : <><Zap size={16} /> Mark absentees <span>(&lt;10s)</span><ChevronRight size={15} /></>}</button>}{item.tone === 'scheduled' && <span className="scheduled-note"><Clock3 size={15} /> Upcoming</span>}</div></article>
}
