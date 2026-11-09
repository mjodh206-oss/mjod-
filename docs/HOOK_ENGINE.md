# Hook Engine — 20 formulas for educational Shorts

A hook is not an intro. It is a **promise of a payoff**, delivered in ≤ 12 words, in the first
0.5–3 seconds, spoken AND on screen. Score every hook before filming.

## Scoring rubric (pass = 8/10)

| Point | Test |
|---|---|
| +2 | No greeting, no "today we'll…", no logo |
| +2 | Contains a contradiction, a number, or a direct "you" |
| +2 | ≤ 12 words / ≤ 3 seconds spoken |
| +2 | Creates an unanswered question (open loop) |
| +1 | Specific (a number, a noun, a named thing) instead of vague |
| +1 | Payoff actually exists and is delivered by the end |

## The 20 formulas

Each has a fill-in template and an example from this channel's first five episodes.

| # | Family | Template | Example |
|---|---|---|---|
| 1 | Contradiction | `X is actually not X.` | "A day on Venus is longer than its year." |
| 2 | Number shock | `[Big number]. That's [surprising unit].` | "243 days. That's one single spin." |
| 3 | The you-test | `Try to [action]. You can't. Here's why.` | "Try tickling yourself. Nothing. Here's why." |
| 4 | Forbidden | `Your [organ] is hiding this from you.` | "Your brain cancels touch before you feel it." |
| 5 | False belief | `You probably think [X]. It's the opposite.` | "The night sky should be blindingly bright." |
| 6 | Time collapse | `[Huge span], in [tiny span].` | "Three thousand years old. Still honey." |
| 7 | Cost of not knowing | `This is why you keep [everyday failure].` | "This is why names vanish ten seconds later." |
| 8 | Removed rule | `Delete [one thing] and [system] breaks.` | "Delete one brain region and tickling works." |
| 9 | Ranking | `The weirdest [thing] is not the one you think.` | "The strangest planet isn't the hottest one." |
| 10 | Question with answer withheld | `Why does [odd thing]? The answer isn't [obvious].` | "Why is space black? Not because there are no stars." |
| 11 | Two-fact collision | `[Fact A]. And [fact B]. Both true.` | "It spins backwards. And its day outlasts its year." |
| 12 | Second-person error | `You've been [doing X] wrong your whole life.` | "You've been remembering names the hard way." |
| 13 | Invisible | `This is happening in your body right now.` | "Right now your brain is deleting a sensation." |
| 14 | Object focus | `This [object] outlived [civilisation].` | "This jar outlived every empire that made it." |
| 15 | Countdown | `Three reasons [X]. Number two is the weird one.` | "Two clocks on Venus. Both disagree with the Sun." |
| 16 | Bet | `Bet you can't [simple task].` | "Bet you can't make yourself laugh by tickling." |
| 17 | Scale jump | `Zoom out far enough and [normal thing] breaks.` | "Zoom out far enough and the sky catches fire." |
| 18 | Name-drop paradox | `[Name] sticks. [Same name] doesn't. Why?` | "Baker the job sticks. Baker the name doesn't." |
| 19 | One-word correction | `[Common word] is the wrong word.` | "'Infinite' is the word that breaks the sky." |
| 20 | Loop-closing tease | `By the end of this, [X] will make sense.` | "By the end, one sunrise will make no sense." |

## Anti-patterns (instant fail)

- "Hey everyone, welcome back to the channel…"
- "In this video I'm going to talk about…"
- "Did you know that…?" with no twist.
- A hook that the payoff doesn't honour (this is the fastest way to kill retention).
- More than one idea in the hook.
- All-caps hype, emoji stacks, "VIRAL", "YOU WON'T BELIEVE".

## A/B protocol

1. Write **3 hooks** per episode from 3 *different* families (stored in `episodes/*.json`).
2. Film once, publish with hook #1. Log the 3 s hold rate in `reports/`.
3. Next week, use the winning family on a different topic; retire the losing family.
4. Compare **families**, not individual lines — one video is noise, ten videos is a signal.

`python tools/hook_engine.py "why honey never spoils"` generates fresh variants offline from
these templates. Use them as a starting point, then rewrite in your own voice.
