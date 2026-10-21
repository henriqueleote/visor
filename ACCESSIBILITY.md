# Accessibility

Visor should work for everyone who wears Meta glasses, including people who use a screen reader, large text, or the glasses themselves as an accessibility aid. This file says what Visor aims for, what does not work yet, and how to report a problem.

## Goals

- The app meets WCAG 2.2 level AA where it applies to an Android app, and follows Android's accessibility guidelines.
- Every task works with TalkBack. Each control reads a label, and a switch or a choice reads its state.
- Every screen works at the largest font size and display size. Text sizes are in `sp` and follow the phone's setting.
- Text has a contrast of at least 4.5:1 with its background. Icons and the edges of controls have at least 3:1.
- Touch targets are at least 48 by 48 dp.
- Nothing flashes more than three times a second.

## What works today

- "Hey Meta" goes to the model you choose. It can see what the glasses' camera sees and answers in the glasses' own voice, so the glasses can describe a scene or read a sign.
- Settings holds the glasses' own accessibility settings, such as Detailed responses, Mono audio and Speech speed.
- All text is sized in `sp` and follows the phone's font size.
- Body text is white or grey (`#86868B`) on black and dark grey, at 4.7:1 or more.
- The app is in English, Italian and Russian.

## Known limitations

This list comes from a review of the code on 2026-10-21, not from a test with TalkBack. None of these is fixed yet.

- Gallery thumbnails have no label. TalkBack reads a photo as a button with no name.
- Some other icons and images have no description. Most are decorative, but a few are the only content of a control.
- Screen titles are not marked as headings, so TalkBack cannot jump from one to the next.
- There is only a dark theme, and no high-contrast option.
- Some small text and icons use dark grey (`#48484A`) on black, at 2.3:1: the chevrons at the end of rows, hints in empty text fields, and times in the event log.
- Visor ignores the phone's "Remove animations" setting. Indicators in the Live view, the gallery and the Meta AI page loop while they show.
- Some round buttons are 44 dp, under the 48 dp target.
- At large font sizes, labels that are one line long are cut off with an ellipsis.
- Setup shows Meta's login screens, which Visor draws from Meta's own layout. They have not been tried with TalkBack.

## Supported environments

- Android 10 or later, on a phone.
- TalkBack, the phone's font size and its display size. Visor follows all three.
- Not tried: Switch Access, Voice Access, Select to Speak, external keyboards and braille displays. Reports about them are welcome.

## Contributor requirements

For a pull request that changes what a screen shows:

1. Turn on TalkBack and go through the change. Every control reads a label, and its state if it has one.
2. Set the largest font size and display size. Nothing important is cut off or overlaps.
3. Give each icon or image that is the only content of a control a `contentDescription` from the strings, in `values/` and `values-it/`. Pass `null` only for a decorative one.
4. Use `sp` for text and the colours in `Visor.colors`. New text stays at 4.5:1 or more against its background.
5. Keep touch targets at 48 dp or more.

The pull request template has a line for this.

## Reporting a problem

Open an issue with the [accessibility form](https://github.com/macedonga/visor/issues/new?template=accessibility.yml). Say what you tried to do, what stopped you, and which assistive technology and settings you use. For a question, ask in [Discussions](https://github.com/macedonga/visor/discussions/categories/q-a).

Every report gets an answer. A problem that stops someone from doing a task comes before everything else. If something cannot be fixed, the answer says why, and gives a way around it when there is one.
