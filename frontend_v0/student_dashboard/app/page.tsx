'use client'

import { useMemo, useState } from 'react'
import { Bell, CalendarDays, Check, ChevronDown, ClipboardList, Clock3, History, LayoutDashboard, Moon, Sun, TrendingUp, X } from 'lucide-react'

const subjects = [
  { code: 'CS601', name: 'Cloud Computing', type: 'Theory', attended: 24, total: 28, sessions: [['08 Sep', 'Present'], ['03 Sep', 'Present'], ['01 Sep', 'Absent'], ['29 Aug', 'Present'], ['27 Aug', 'Present']] },
  { code: 'CS602', name: 'Database Systems', type: 'Theory', attended: 19, total: 24, sessions: [['05 Sep', 'Present'], ['03 Sep', 'Absent'], ['29 Aug', 'Present'], ['27 Aug', 'Present'], ['22 Aug', 'Absent']] },
  { code: 'CS603', name: 'Operating Systems', type: 'Theory', attended: 22, total: 26, sessions: [['06 Sep', 'Present'], ['04 Sep', 'Present'], ['30 Aug', 'Present'], ['28 Aug', 'Present'], ['26 Aug', 'Absent']] },
  { code: 'CS604', name: 'AI / Machine Learning', type: 'Theory', attended: 21, total: 25, sessions: [['07 Sep', 'Present'], ['02 Sep', 'Present'], ['30 Aug', 'Present'], ['25 Aug', 'Present'], ['23 Aug', 'Present']] },
  { code: 'CS605L', name: 'Cloud Lab', type: 'Lab', attended: 28, total: 32, sessions: [['06 Sep', 'Present'], ['30 Aug', 'Present'], ['23 Aug', 'Absent'], ['16 Aug', 'Present'], ['09 Aug', 'Present']] },
]

const lectures = [
  { time: '09:00 – 10:00 AM', code: 'CS601', title: 'Cloud Computing', detail: 'Prof. S. Sharma · Room 408', status: 'Marked Present', kind: 'present' },
  { time: '10:00 – 11:00 AM', code: 'CS602', title: 'Database Systems', detail: 'Prof. R. Rao · Room 408', status: 'Marked Absent', kind: 'absent' },
  { time: '11:15 – 01:15 PM', code: 'CS605L', title: 'Cloud Lab (Batch B3)', detail: 'Lab 302', status: 'Upcoming', kind: 'upcoming' },
]

function AttendanceRing({ value }: { value: number }) {
  const circumference = 2 * Math.PI * 49
  const offset = circumference - (value / 100) * circumference
  const state = value >= 75 ? 'safe' : value >= 65 ? 'warn' : 'risk'
  return <div className="attendance-ring" role="img" aria-label={`Overall attendance ${value.toFixed(1)} percent`}>
    <svg viewBox="0 0 120 120" aria-hidden="true"><circle className="ring-track" cx="60" cy="60" r="49" /><circle className={`ring-progress ${state}`} cx="60" cy="60" r="49" strokeDasharray={circumference} strokeDashoffset={offset} /></svg>
    <div className="ring-copy"><strong>{value.toFixed(1)}%</strong><span>Overall attendance</span><small>114 / 140 conducted</small></div>
  </div>
}

function SubjectCard({ subject }: { subject: typeof subjects[number] }) {
  const [open, setOpen] = useState(false)
  const percentage = (subject.attended / subject.total) * 100
  const safeSkips = Math.max(0, Math.floor((subject.attended - subject.total * .75) / .75))
  return <article className="subject-card">
    <button className="subject-trigger" onClick={() => setOpen(!open)} aria-expanded={open}>
      <div><div className="subject-meta"><span className="mono accent">{subject.code}</span><span className="type-pill">{subject.type}</span></div><h3>{subject.name}</h3></div><ChevronDown className={open ? 'chevron open' : 'chevron'} aria-hidden="true" />
      <div className="subject-stats"><span><b className="mono">{subject.attended}/{subject.total}</b> lectures</span><b className={`mono ${percentage >= 75 ? 'safe-text' : 'warn-text'}`}>{percentage.toFixed(1)}%</b></div>
      <div className="progress-track" aria-label={`${percentage.toFixed(1)} percent attendance`}><span style={{ width: `${percentage}%` }} className={percentage >= 75 ? 'safe-fill' : 'warn-fill'} /></div>
      <div className="safe-row"><span>Safe skips</span><b className="mono">{safeSkips} {safeSkips === 1 ? 'lecture' : 'lectures'}</b></div>
    </button>
    {open && <div className="session-history"><span className="eyebrow">Recent sessions</span>{subject.sessions.map(([date, status]) => <div className="session-row" key={date}><span className="mono">{date}</span><span className={status === 'Present' ? 'safe-text' : 'risk-text'}>{status}</span></div>)}</div>}
  </article>
}

export default function Page() {
  const [target, setTarget] = useState(75)
  const [light, setLight] = useState(false)
  const overall = 81.4
  const safeSkips = Math.max(0, Math.floor((114 - (target / 100) * 140) / (target / 100)))
  const required = useMemo(() => Math.max(0, Math.ceil((target * 140 - 114 * 100) / (100 - target))), [target])
  return <main className={light ? 'app-shell light-mode' : 'app-shell'}>
    <div className="page-wrap">
      <header className="topbar"><div className="brand"><div className="brand-mark">OA</div><div><strong>OpenAttend</strong><span>VESIT · Term: Sem VI (Even 2026)</span></div></div><div className="top-actions"><button className="icon-button" onClick={() => setLight(!light)} aria-label="Toggle light and dark theme">{light ? <Moon /> : <Sun />}</button><button className="icon-button notification" aria-label="Notifications, 2 unread"><Bell /><i>2</i></button><div className="profile"><div className="avatar">VG</div><div><strong>Vedant Gharat</strong><span className="mono">Roll No: 2024CS38 · D12B CMPN</span></div></div></div></header>
      <section className="intro"><div><span className="eyebrow accent">Monday, 8 September 2026</span><h1>Good morning, Vedant.</h1><p>Your attendance pulse is looking steady. Keep the momentum.</p></div><span className="division-badge">Division D12B</span></section>
      <section className="hero-grid"><div className="ring-panel"><AttendanceRing value={overall} /><div className="threshold-note"><span className="status-dot safe-dot" /> Statutory 75% Threshold</div></div><div className="predictor-grid"><article className="predictor safe-card"><div className="card-heading"><TrendingUp /><span>On track</span></div><strong className="big-number mono">{safeSkips}</strong><h2>Safe to Skip</h2><p>You can miss up to {safeSkips} upcoming lectures and remain ≥ {target}%.</p></article><article className={`predictor ${overall < target ? 'risk-card' : 'neutral-card'}`}><div className="card-heading"><Clock3 /><span>Target predictor</span></div><strong className="big-number mono">{required}</strong><h2>Must Attend Recovery</h2><p>{required ? `Attend the next ${required} lectures to reach ${target}%.` : `No recovery lectures required at ${target}%.`}</p></article><div className="slider-card"><div className="slider-label"><label htmlFor="target">Preview target attendance</label><b className="mono accent">{target}%</b></div><input id="target" type="range" min="75" max="85" value={target} onChange={(event) => setTarget(Number(event.target.value))} aria-label="Target attendance percentage" /><div className="slider-scale mono"><span>75%</span><span>80%</span><span>85%</span></div></div></div></section>
      <section className="section"><div className="section-heading"><div><span className="eyebrow">Your day</span><h2>Today&apos;s lectures</h2></div><button className="text-button">View timetable</button></div><div className="lecture-row">{lectures.map((lecture) => <article className="lecture-card" key={lecture.time}><div className="lecture-time mono">{lecture.time}<span className={`status-dot ${lecture.kind}-dot`} /></div><span className="mono accent code">{lecture.code}</span><h3>{lecture.title}</h3><p>{lecture.detail}</p><span className={`lecture-status ${lecture.kind}`}>{lecture.kind === 'present' ? <Check /> : lecture.kind === 'absent' ? <X /> : <Clock3 />}{lecture.status}</span></article>)}</div></section>
      <section className="section"><div className="section-heading"><div><span className="eyebrow">Performance</span><h2>Subject-wise breakdown</h2></div><span className="mono muted">5 subjects</span></div><div className="subject-grid">{subjects.map((subject) => <SubjectCard key={subject.code} subject={subject} />)}</div></section>
    </div>
    <nav className="bottom-nav" aria-label="Quick navigation"><NavItem icon={<LayoutDashboard />} label="Dashboard" active /><NavItem icon={<CalendarDays />} label="Timetable" /><NavItem icon={<History />} label="History" /><NavItem icon={<ClipboardList />} label="OD Leave" /></nav>
  </main>
}

function NavItem({ icon, label, active = false }: { icon: React.ReactNode; label: string; active?: boolean }) { return <button className={active ? 'nav-item active' : 'nav-item'} aria-current={active ? 'page' : undefined}>{icon}<span>{label}</span></button> }
