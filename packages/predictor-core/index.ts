/**
 * Predictor Core Module - Pure Functions
 * Formulas derived from PRD.md §6.4
 */

export interface PredictorInput {
  present: number;
  total: number;
  threshold?: number; // default 0.75 (75%)
  remainingPlanned?: number; // R
}

export interface PredictorResult {
  currentPct: number;
  threshold: number;
  isRecoverable: boolean;
  safeSkips: number;
  mustAttend: number;
  headline: string;
  explanation: string;
}

export function calculatePredictor(input: PredictorInput): PredictorResult {
  const { present, total, remainingPlanned = 100 } = input;
  const threshold = input.threshold ?? 0.75;

  if (total === 0) {
    return {
      currentPct: 0,
      threshold,
      isRecoverable: true,
      safeSkips: 0,
      mustAttend: 0,
      headline: 'No lectures held yet',
      explanation: 'Attendance data will populate after the first recorded lecture.'
    };
  }

  const currentPct = (present / total) * 100;

  // Case A: Above or equal to threshold -> Calculate safe skips
  if (currentPct / 100 >= threshold) {
    // P / (T + x) >= threshold  =>  x = floor(P / threshold) - T
    const rawX = Math.floor(present / threshold) - total;
    const safeSkips = Math.max(0, Math.min(rawX, remainingPlanned));

    return {
      currentPct,
      threshold,
      isRecoverable: true,
      safeSkips,
      mustAttend: 0,
      headline: safeSkips > 0 
        ? `You can miss ${safeSkips} more lecture${safeSkips === 1 ? '' : 's'} safely`
        : `You are on the ${Math.round(threshold * 100)}% threshold margin`,
      explanation: `With ${present} present out of ${total} total lectures held, your attendance is ${currentPct.toFixed(1)}%.`
    };
  }

  // Case B: Below threshold -> Calculate required consecutive attendance
  // (P + y) / (T + y) >= threshold  =>  y = ceil((threshold * T - P) / (1 - threshold))
  const rawY = Math.ceil((threshold * total - present) / (1 - threshold));

  if (rawY > remainingPlanned) {
    return {
      currentPct,
      threshold,
      isRecoverable: false,
      safeSkips: 0,
      mustAttend: rawY,
      headline: `Not mathematically recoverable this term`,
      explanation: `Requires attending ${rawY} consecutive lectures, but only ${remainingPlanned} lectures remain.`
    };
  }

  return {
    currentPct,
    threshold,
    isRecoverable: true,
    safeSkips: 0,
    mustAttend: rawY,
    headline: `Attend the next ${rawY} lecture${rawY === 1 ? '' : 's'} in a row`,
    explanation: `Attending ${rawY} consecutive lectures will bring your attendance from ${currentPct.toFixed(1)}% back to ${(threshold * 100).toFixed(0)}%.`
  };
}
