# UI behavior

Browse: date header, today's current-time line, a scrollable elapsed-time
grid, clipped timed events, separate scrollable all-day chips. Existing
events open a read-only detail card inside DayBricks with description,
location, compact date and time, and event time zone when available. A footer
row offers delete, open in calendar, and close, in that physical order.
Deletion asks for confirmation and warns when it removes a recurring series.
A device setting opens the calendar app directly on event tap.
Day arrows preserve local time of day and zoom. Horizontal swipes do not
change the date.

Template selection: **+** toggles the library. Side presentation compresses
the calendar. Bottom presentation has its own close/drag handle. Side
swipes ignore a 32dp edge zone; a right swipe on either the calendar or the
open side panel closes it. A dragged panel tracks the finger, then always
settles fully open or closed. Template actions: edit, delete, move up/down.
The editor uses AndroidX Emoji Picker for one activity icon, including categories,
variants and recently used emoji. Activity options retain the template icon, select
their title and optional duration; Custom allows manual title.

Placement: the library closes unless persistent. The draft is a preview,
not a provider event. Vertical scrolling moves the timeline around its
center; snapping is five elapsed minutes. Virtual space at both ends lets
midnight and late-night starts reach the anchor. Horizontal ruler dragging
sets any whole-minute duration from 1 to 720 and shows a large translucent
duration plus start/end overlay at screen center. Buttons and accessibility
actions also adjust duration; zoom buttons animate the scale and preserve the
viewport anchor. The top of the draft card, rather than its center, marks the
event start.

The confirm FAB is physically right for LTR and left for RTL. Cancel is
opposite; system Back cancels too. An active
insert locks the draft until the result arrives. Failed inserts keep the
draft. Success remembers the event's end for this date in the current
session. New activities use that end or the viewport, never an arbitrary 08:00.
After insertion DayBricks requests an expedited manual sync for the selected
non-local Android calendar and immediately refreshes its provider flow.

Layout policy uses available width, height and their ratio, not device labels.
Windows at least 780×480dp use persistent panes. Compact-height windows use a
side pane. Taller windows use bottom only below 320dp wide or when their
width/height ratio is below 0.42; otherwise they use side. Pane width/height
also scale as a fraction of the current window. The transient side pane shares
the width with a compressed calendar strip; event geometry remains visible but
card text is hidden until the pane closes. Explicit bottom always wins; explicit side falls back safely
below 280dp. The activity requests a 280×360dp minimum freeform window, while
the UI still has compact fallbacks because launchers may not enforce it. Font scaling
does not shrink interaction targets. A separating/occluding hinge confines
the screen to a continuous usable region so no main control lies beneath it.

Controls occupy their own safe, IME-aware area rather than hiding the end
of a scrollable day. Small event cards omit secondary text; narrow cards
become occupancy marks. Owned events have an accent border and block mark,
so ownership does not rely on color alone. All-day headers are bounded.

Panel and zoom transitions animate without delaying state changes. Theme can
follow the system or be forced to Light or Dark. In both modes, elevated
surface containers, outlines and dividers separate the top, bottom and side
controls from the calendar.
Custom ruler drawing uses one fractional scalar and mathematical tick
scale/opacity, not an animation object per tick.
