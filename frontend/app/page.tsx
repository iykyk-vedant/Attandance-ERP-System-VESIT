'use client'

import { useState } from 'react'
import StudentDashboard from './student/page'
import FacultyDashboard from './faculty/page'
import ClassTeacherDashboard from './classteacher/page'
import { GraduationCap, Users, ClipboardList, ShieldAlert, Moon, Sun, ArrowUpRight } from 'lucide-react'

export default function OpenAttendHub() {
  const [activeRole, setActiveRole] = useState<'student' | 'faculty' | 'classteacher'>('student')

  return (
    <div>
      {/* Top Floating Multi-Role Switcher Bar */}
      <div className="fixed top-3 left-1/2 -translate-x-1/2 z-50 bg-[#1b1e23]/90 backdrop-blur-md border border-[#2b3038] px-3 py-1.5 rounded-full shadow-2xl flex items-center gap-1 text-xs">
        <span className="text-[#5fbfa6] font-bold px-2 flex items-center gap-1.5 border-r border-[#2b3038]">
          <span className="w-2 h-2 rounded-full bg-[#5fbfa6] animate-pulse"></span>
          OpenAttend ERP
        </span>

        <button
          onClick={() => setActiveRole('student')}
          className={`flex items-center gap-1.5 px-3 py-1 rounded-full font-medium transition-all ${
            activeRole === 'student'
              ? 'bg-[#5fbfa6] text-[#14161a] font-bold shadow-md'
              : 'text-[#9b9a92] hover:text-[#ecebe6]'
          }`}
        >
          <GraduationCap size={14} />
          Student (75% Predictor)
        </button>

        <button
          onClick={() => setActiveRole('faculty')}
          className={`flex items-center gap-1.5 px-3 py-1 rounded-full font-medium transition-all ${
            activeRole === 'faculty'
              ? 'bg-[#5fbfa6] text-[#14161a] font-bold shadow-md'
              : 'text-[#9b9a92] hover:text-[#ecebe6]'
          }`}
        >
          <Users size={14} />
          Faculty (Marking & 48h)
        </button>

        <button
          onClick={() => setActiveRole('classteacher')}
          className={`flex items-center gap-1.5 px-3 py-1 rounded-full font-medium transition-all ${
            activeRole === 'classteacher'
              ? 'bg-[#5fbfa6] text-[#14161a] font-bold shadow-md'
              : 'text-[#9b9a92] hover:text-[#ecebe6]'
          }`}
        >
          <ClipboardList size={14} />
          Class Teacher (Defaulters)
        </button>

        <a
          href="/api/v1/health"
          target="_blank"
          className="text-[#6b6a62] hover:text-[#5fbfa6] px-2 flex items-center gap-1 border-l border-[#2b3038] ml-1"
          title="Spring Boot Backend API Status"
        >
          Backend API <ArrowUpRight size={12} />
        </a>
      </div>

      {/* Render Active v0 Dashboard */}
      <div className="pt-10">
        {activeRole === 'student' && <StudentDashboard />}
        {activeRole === 'faculty' && <FacultyDashboard />}
        {activeRole === 'classteacher' && <ClassTeacherDashboard />}
      </div>
    </div>
  )
}
