# YTKAB0BP's Contest Entry. December 2024

## StoryRecorder integration into photo picker
- Adapted open/close animation from a preview.
- Applied zoom animation from PhotoViewer to attach camera as well.
- Changed flip button paddings according to mockups, also applied to StoryRecorder.
- Changed actionbar's buttons order according to mockups.
- Changed lock icon animation according to mockups, also applied to StoryRecorder.
- Note: Collages & dual camera auto-disables themselves when closing camera. This can be changed later in StoryRecorder#close, no mockups/info provided about that.
- Note: Not forcing backwards-facing camera when dismissing camera like it was before StoryRecorder.
- Note: Using StoryRecorder's editor for smooth transition & widgets support (Instead of PhotoViewer's one).
- Fixed StoryRecorder's camera thumbnail rendering as required for the attach camera. (Also applied to the StoryRecorder when pausing camera because of gallery)
- Supported landscape layout as well. But haven't changed controls for it, no mockups provided.
- Supported long press buttons in attach camera as well (Scheduled messages, send as file, etc.).
- Supported chat restrictions (no video permissions).
- Using PhotoViewer after StoryRecorder for avatars. This way user can check how well avatar will be cropped to circle with full compatibility.
- Fixed StoryRecorder's mode switcher stuck between positions with system back gesture enabled. (MotionEvent's cancel action, fix applied to both attach camera & story camera)

## Other
- Changed PhotoViewer's interpolator to match StoryRecorder one, also making open/close animation much smoother.
- Fixed widgets sticky center, now considering inverse photo scale (Now always 12dp, was 12dp * scale because of widgets layout).
- Disabled overscroll in photo picker. Fixes strange camera cell translation (Camera preview stays without translation, no SDK API available for syncing with system overscroll).
- Fixed memory leaks in AndroidUtilities#emptyMotionEvent() usages. Motion events should be recycled after usage.

P.S. Sorry for attaching hint on top of dialogs activity, comments on contest.com are no longer available :(

Dialogs top hint is disabled by default in source code, it's checked by BuildVars#CONTEST_CONTESTANT