/**
 * Builds hands for tables, for test data and for legacy tables that have only totals.
 * The scoring rules match `handScoring.ts`: a win of score s gives the winner s + 24 and takes s + 8 from the
 * discarder and 8 from each other seat. A self-draw takes s + 8 from every other seat.
 */
import { MAX_CHICKEN_HAND_SCORE, MIN_HAND_SCORE } from "./handScoring";

export type Rng = () => number;

/** A small seeded generator, so a run gives the same data again. */
export function seededRng(seed: number): Rng {
  let state = seed >>> 0;
  return () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let t = state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export type SeatNumbers = [number, number, number, number];

export type SynthHand = {
  /** Seat index 0 to 3 (east, south, west, north). Null for a drawn hand. */
  winner: number | null;
  /** Seat index of the discarder. Null for a self-draw and for a drawn hand. */
  loser: number | null;
  score: number;
  isChicken: boolean;
  penalties: SeatNumbers;
};

const ZERO: SeatNumbers = [0, 0, 0, 0];

export function seatChange(hand: SynthHand): SeatNumbers {
  const change: SeatNumbers = [0, 0, 0, 0];
  if (hand.winner != null) {
    for (let seat = 0; seat < 4; seat += 1) {
      if (hand.loser == null) {
        change[seat] = seat === hand.winner ? (hand.score + MIN_HAND_SCORE) * 3 : -(hand.score + MIN_HAND_SCORE);
      } else if (seat === hand.winner) {
        change[seat] = hand.score + MIN_HAND_SCORE * 3;
      } else if (seat === hand.loser) {
        change[seat] = -(hand.score + MIN_HAND_SCORE);
      } else {
        change[seat] = -MIN_HAND_SCORE;
      }
    }
  }
  return change.map((value, seat) => value + hand.penalties[seat]) as SeatNumbers;
}

export function sumChanges(hands: SynthHand[]): SeatNumbers {
  const total: SeatNumbers = [0, 0, 0, 0];
  hands.forEach((hand) => seatChange(hand).forEach((value, seat) => { total[seat] += value; }));
  return total;
}

const pick = <T>(rng: Rng, items: T[]): T => items[Math.floor(rng() * items.length)];

/** A hand score. Most hands are small, a few are large, and one in a hundred is a limit hand. */
export function randomScore(rng: Rng, cap: number): number {
  const base = MIN_HAND_SCORE + Math.floor(-Math.log(1 - rng()) * 9);
  const rare = rng() < 0.015 ? 30 + Math.floor(rng() * 50) : 0;
  return Math.max(MIN_HAND_SCORE, Math.min(cap, base + rare));
}

/** Every way to win: four self-draws and twelve discards. */
function handTypes(): Array<{ winner: number; loser: number | null }> {
  const types: Array<{ winner: number; loser: number | null }> = [];
  for (let winner = 0; winner < 4; winner += 1) {
    types.push({ winner, loser: null });
    for (let loser = 0; loser < 4; loser += 1) if (loser !== winner) types.push({ winner, loser });
  }
  return types;
}
const TYPES = handTypes();

/** Finds the one hand that gives exactly [residual]. Scores must stay between the minimum and [cap]. */
function solveOne(residual: SeatNumbers, cap: number): SynthHand | null {
  for (const type of TYPES) {
    const candidate = type.loser == null
      ? (residual[type.winner] - MIN_HAND_SCORE * 3) / 3
      : residual[type.winner] - MIN_HAND_SCORE * 3;
    if (!Number.isInteger(candidate) || candidate < MIN_HAND_SCORE || candidate > cap) continue;
    const hand: SynthHand = { winner: type.winner, loser: type.loser, score: candidate, isChicken: false, penalties: [...ZERO] };
    const change = seatChange(hand);
    if (change.every((value, seat) => value === residual[seat])) return hand;
  }
  return null;
}

function subtract(a: SeatNumbers, b: SeatNumbers): SeatNumbers {
  return a.map((value, seat) => value - b[seat]) as SeatNumbers;
}

/** Finds two hands that give exactly [residual], by trying every type and score of the first hand. */
function solveTwo(residual: SeatNumbers, cap: number, rng: Rng): SynthHand[] | null {
  const order = shuffled(TYPES, rng);
  for (const type of order) {
    for (let score = MIN_HAND_SCORE; score <= cap; score += 1) {
      const first: SynthHand = { winner: type.winner, loser: type.loser, score, isChicken: false, penalties: [...ZERO] };
      const second = solveOne(subtract(residual, seatChange(first)), cap);
      if (second != null) return [first, second];
    }
  }
  return null;
}

/** Finds up to [maxHands] hands that add up to [residual]. Returns null when no tries succeed. */
export function solveResidual(
  residual: SeatNumbers,
  maxHands: number,
  cap: number,
  rng: Rng,
  tries = 12,
): SynthHand[] | null {
  if (residual.every((value) => value === 0)) return [];
  const upper = Math.max(cap, MIN_HAND_SCORE);
  const single = maxHands >= 1 ? solveOne(residual, upper) : null;
  if (single != null) return [single];
  if (maxHands < 2) return null;
  for (let attempt = 0; attempt < tries; attempt += 1) {
    const extra = Math.min(maxHands - 2, attempt % 3);
    const prefix: SynthHand[] = [];
    for (let index = 0; index < extra; index += 1) {
      const type = pick(rng, TYPES);
      prefix.push({ winner: type.winner, loser: type.loser, score: randomScore(rng, upper), isChicken: false, penalties: [...ZERO] });
    }
    const pair = solveTwo(subtract(residual, sumChanges(prefix)), upper, rng);
    if (pair != null) return [...prefix, ...pair];
  }
  return null;
}

export type SynthesisInput = {
  /** Final seat scores that the hands must reach. */
  target: SeatNumbers;
  /** Hands that stay as they are. They count in the total. */
  kept?: SynthHand[];
  /** Number of hand slots that are still free. */
  slots: number;
  /** Scores of the best hands that the table must keep. Empty when unknown. */
  bestScores?: number[];
  /** Number of chicken hands that the table must keep. */
  chickenCount?: number;
  /** How many hands a new table should have, including draws. */
  handCount?: number;
  rng: Rng;
};

export type SynthesisResult = {
  hands: SynthHand[];
  /** Penalty that was needed because the totals do not add up to zero. */
  penaltyAmount: number;
  /** True when the best hand scores could not be kept. */
  bestScoresBroken: boolean;
};

/**
 * Returns new hands that, with the kept hands, add up to the target scores. Returns null when no hands fit.
 * Totals whose sum is not zero cannot come from hands alone, so one penalty carries the difference.
 */
export function synthesizeHands(input: SynthesisInput): SynthesisResult | null {
  const { rng } = input;
  const kept = input.kept ?? [];
  const targetSum = input.target.reduce((a, b) => a + b, 0);
  const keptSum = sumChanges(kept);
  const keptPenalty = kept.reduce((sum, hand) => sum + hand.penalties.reduce((a, b) => a + b, 0), 0);
  const penaltyAmount = targetSum - keptPenalty;
  const best = (input.bestScores ?? []).filter((score) => score >= MIN_HAND_SCORE);
  const cap = best.length > 0 ? Math.max(MIN_HAND_SCORE, Math.min(...best) - 1) : 70;
  const chickenCount = Math.min(input.chickenCount ?? 0, Math.max(0, input.slots - best.length));
  const wanted = Math.min(input.slots, Math.max(input.handCount ?? 15, 0) - kept.length);
  if (wanted < 0) return null;

  for (let attempt = 0; attempt < 300; attempt += 1) {
    const fixed: SynthHand[] = [];
    // Players with a high target win more often. Players with a low target discard more often.
    const weightedSeat = (weights: number[], skip: number | null): number => {
      const total = weights.reduce((sum, value, seat) => sum + (seat === skip ? 0 : value), 0);
      let value = rng() * total;
      for (let seat = 0; seat < 4; seat += 1) {
        if (seat === skip) continue;
        value -= weights[seat];
        if (value <= 0) return seat;
      }
      return skip === 3 ? 2 : 3;
    };
    const winWeights = input.target.map((value) => Math.max(value, 0) + 25);
    const loseWeights = input.target.map((value) => Math.max(-value, 0) + 25);
    const make = (score: number, isChicken: boolean): SynthHand => {
      const winner = weightedSeat(winWeights, null);
      const loser = rng() < 0.3 ? null : weightedSeat(loseWeights, winner);
      return { winner, loser, score, isChicken, penalties: [...ZERO] };
    };
    best.forEach((score) => fixed.push(make(score, false)));
    for (let index = 0; index < chickenCount; index += 1) {
      fixed.push(make(MIN_HAND_SCORE + Math.floor(rng() * (MAX_CHICKEN_HAND_SCORE - MIN_HAND_SCORE + 1)), true));
    }
    // Draws and ordinary hands fill the table up to the wanted count. The last hands are solved exactly.
    const solved = 3;
    const filler = Math.max(0, wanted - fixed.length - solved);
    const draws = Math.min(filler, Math.floor(rng() * 3));
    for (let index = 0; index < draws; index += 1) {
      fixed.push({ winner: null, loser: null, score: 0, isChicken: false, penalties: [...ZERO] });
    }
    for (let index = 0; index < filler - draws; index += 1) fixed.push(make(randomScore(rng, cap), false));
    if (fixed.length > input.slots) return null;

    // The penalty goes on one fixed hand, or on a solved hand later when there is no fixed hand.
    const penalized = fixed.length > 0 ? fixed[Math.floor(rng() * fixed.length)] : null;
    if (penalized != null && penaltyAmount !== 0) penalized.penalties[Math.floor(rng() * 4)] += penaltyAmount;

    const residual = subtract(subtract(input.target, keptSum), sumChanges(fixed));
    const free = input.slots - fixed.length;
    if (free < 0) return null;
    if (penalized == null && penaltyAmount !== 0) {
      // No hand can carry the penalty yet. Try again with the penalty inside the residual.
      const seat = Math.floor(rng() * 4);
      const adjusted = [...residual] as SeatNumbers;
      adjusted[seat] -= penaltyAmount;
      const hands = solveResidual(adjusted, Math.min(free, 4), cap, rng);
      if (hands != null && hands.length > 0) {
        hands[0].penalties[seat] += penaltyAmount;
        return finish([...fixed, ...hands], best, penaltyAmount);
      }
      continue;
    }
    const hands = solveResidual(residual, Math.min(free, 4), cap, rng);
    if (hands != null) return finish([...fixed, ...hands], best, penaltyAmount);
  }
  return null;
}

function finish(hands: SynthHand[], best: number[], penaltyAmount: number): SynthesisResult {
  const scores = hands.filter((hand) => hand.winner != null).map((hand) => hand.score).sort((a, b) => b - a);
  const expected = [...best].sort((a, b) => b - a);
  const top = scores.slice(0, expected.length);
  const bestScoresBroken = expected.length > 0 && expected.some((score, index) => top[index] !== score);
  return { hands, penaltyAmount, bestScoresBroken };
}

/** Random order for the generated hands, so the strong hands are not always the first ones. */
export function shuffled<T>(items: T[], rng: Rng): T[] {
  const copy = [...items];
  for (let index = copy.length - 1; index > 0; index -= 1) {
    const other = Math.floor(rng() * (index + 1));
    [copy[index], copy[other]] = [copy[other], copy[index]];
  }
  return copy;
}

/**
 * A full random table for test tournaments. [strength] has one positive value for each seat.
 * Stronger players win more often and discard less often.
 */
export function randomTable(strength: SeatNumbers, rng: Rng, handCount = 16): SynthHand[] {
  const hands: SynthHand[] = [];
  const weighted = (weights: number[]): number => {
    const total = weights.reduce((a, b) => a + b, 0);
    let value = rng() * total;
    for (let index = 0; index < weights.length; index += 1) {
      value -= weights[index];
      if (value <= 0) return index;
    }
    return weights.length - 1;
  };
  for (let index = 0; index < handCount; index += 1) {
    const penalties: SeatNumbers = [0, 0, 0, 0];
    if (rng() < 0.03) penalties[Math.floor(rng() * 4)] = -10 * (1 + Math.floor(rng() * 3));
    if (rng() < 0.09) {
      hands.push({ winner: null, loser: null, score: 0, isChicken: false, penalties });
      continue;
    }
    const winner = weighted([...strength]);
    const loser = rng() < 0.28
      ? null
      : weighted(strength.map((value, seat) => (seat === winner ? 0 : 1 / value)));
    const score = randomScore(rng, 80);
    hands.push({ winner, loser, score, isChicken: score <= MAX_CHICKEN_HAND_SCORE && rng() < 0.25, penalties });
  }
  return hands;
}
