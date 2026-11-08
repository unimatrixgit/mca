// Owner-confirmed schedule, 8 November 2026. GMT is UTC, not local summer time.
export const ELECTION_RULES = Object.freeze({
  house: Object.freeze({first:'2026-11-15T09:00:00.000Z',weeks:2}),
  imperial: Object.freeze({first:'2026-11-15T15:00:00.000Z',weeks:4}),
});
export function electionDates(kind, now=Date.now(), count=4) {
  const rule=ELECTION_RULES[kind];
  if(!rule || !Number.isFinite(now) || !Number.isInteger(count) || count<1 || count>12) throw new Error('Invalid calendar request');
  const first=Date.parse(rule.first),period=rule.weeks*7*86400000;
  const offset=Math.max(0,Math.ceil((now-first)/period));
  return Array.from({length:count},(_,i)=>new Date(first+(offset+i)*period).toISOString());
}
// Only a verified server-side faction and current office may establish eligibility.
export function imperialVoteAllowance({linked=false,faction=null,houseLeader=false}={}) {
  return linked && faction ? houseLeader===true ? 5 : 1 : 0;
}
