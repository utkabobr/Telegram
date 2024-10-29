package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.math.MathUtils;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class QuickShareComponent {
    public final static boolean USE_SMOOTH_OPEN_VIBRATION = true;

    private final static int VERTICAL_MARGIN_DP = 8;

    private final static int PADDING_DP = 12;
    private final static int OUT_PADDING_DP = 72;
    private final static int AVATAR_DP = 42;

    private final static int TAG_HEIGHT_DP = 24;
    private final static int TAG_PADDING_DP = 8;

    private final static int MAX_DIALOGS_COUNT = 5;

    private ChatActivity mActivity;
    private ChatMessageCell messageCell;

    private Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Rect shadowPad = new Rect();
    private Drawable shadow;

    private boolean isSwiping;
    private boolean isShown;
    private boolean canStartSwipe;
    private float showProgress;
    private float arrowProgress;
    private boolean wasSwipe;

    private List<TLRPC.Dialog> dialogs;
    private float startX, startY;
    private int touchSlop;

    private FrameLayout overlayView;
    private FrameLayout contentView;
    private LinearLayout innerLayout;
    private FrameLayout frameContentView;

    private int selectedIndex = -1;
    private Runnable onFullyShown;
    private boolean isFullyShown;
    private boolean transitionToDialog;

    private Bitmap blurBitmap;
    private boolean wasBlur;
    private Canvas blurCanvas;
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private StaticLayout[] tagLayouts = new StaticLayout[MAX_DIALOGS_COUNT];

    public QuickShareComponent(ChatActivity activity) {
        mActivity = activity;
        textPaint.setTextSize(AndroidUtilities.dp(14));
        // TODO: Theme support?
        textPaint.setColor(Color.WHITE);
        textPaint.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
    }

    public boolean onLongPress(ChatMessageCell cell, float x, float y) {
        dialogs = onGetDialogs();
        if (isShown || dialogs.isEmpty()) return false;

        this.messageCell = cell;
        this.startX = x;
        this.startY = y;
        touchSlop = ViewConfiguration.get(cell.getContext()).getScaledTouchSlop();
        onShowAnimation();
        return true;
    }

    private List<TLRPC.Dialog> onGetDialogs() {
        List<TLRPC.Dialog> from = mActivity.getAccountInstance().getMessagesController().dialogsForward;
        if (from.isEmpty()) return Collections.emptyList();

        List<TLRPC.Dialog> list = new ArrayList<>();
        int i = 0;
        while (i < from.size() && list.size() < MAX_DIALOGS_COUNT) {
            TLRPC.Dialog d = from.get(i);
            if (DialogObject.isUserDialog(d.id)) {
                TLRPC.User user = mActivity.getAccountInstance().getMessagesController().getUser(d.id);
                if (user != null && !user.bot) {
                    list.add(d);
                }
            }
            i++;
        }

        return list;
    }

    private void onShowAnimation() {
        isShown = true;
        selectedIndex = -1;
        canStartSwipe = true;
        isSwiping = false;
        transitionToDialog = false;
        wasSwipe = false;
        isFullyShown = false;
        Arrays.fill(tagLayouts, null);
        onFullyShown = null;

        Context ctx = mActivity.getContext();

        shadow = ContextCompat.getDrawable(ctx, R.drawable.reactions_bubble_shadow).mutate();
        shadowPad.left = shadowPad.top = shadowPad.right = shadowPad.bottom = dp(7);
        shadow.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelShadow, mActivity.themeDelegate), PorterDuff.Mode.MULTIPLY));

        bgPaint.setColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, mActivity.themeDelegate));

        innerLayout = new LinearLayout(ctx);
        innerLayout.setClipChildren(false);
        innerLayout.setClipToPadding(false);
        frameContentView = new FrameLayout(mActivity.getContext()) {
            @SuppressLint("DrawAllocation")
            @Override
            protected void onDraw(@NonNull Canvas canvas) {
                super.onDraw(canvas);

                if (!GLGooeyView.drawingBlur && isFullyShown) {
                    int reqWidth = mActivity.getChatListView().getWidth() - AndroidUtilities.dp(PADDING_DP) * 2, reqHeight = AndroidUtilities.dp(TAG_HEIGHT_DP);
                    if (blurBitmap == null || blurBitmap.getWidth() != reqWidth || blurBitmap.getHeight() != reqHeight) {
                        if (blurBitmap != null) {
                            blurBitmap.recycle();
                        }
                        blurBitmap = Bitmap.createBitmap(reqWidth, reqHeight, Bitmap.Config.ARGB_8888);
                        blurCanvas = new Canvas(blurBitmap);
                        wasBlur = false;

                        ColorMatrix colorMatrix = new ColorMatrix();
                        AndroidUtilities.adjustSaturationColorMatrix(colorMatrix, 2.5f);
                        AndroidUtilities.adjustBrightnessColorMatrix(colorMatrix, 0.1f);

                        BitmapShader shader = new BitmapShader(blurBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                        paint.setShader(shader);
                        paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
                    }

                    if (isRealTimeBlurEnabled() || !wasBlur) {
                        blurBitmap.eraseColor(0);
                        blurCanvas.save();
                        blurCanvas.translate(-contentView.getTranslationX() - contentView.getLeft(), -contentView.getTranslationY() - contentView.getTop() - AndroidUtilities.dp(OUT_PADDING_DP - PADDING_DP) - innerLayout.getTop()
                            + AndroidUtilities.dp(12));
                        mActivity.getContentView().backgroundView.draw(blurCanvas);
                        mActivity.getChatListView().draw(blurCanvas);
                        blurCanvas.drawColor(0x44000000);
                        blurCanvas.restore();
                        Utilities.stackBlurBitmap(blurBitmap, (int) (Math.max(26, Math.max(reqWidth, reqHeight) / 180) * 2.5f));
                        wasBlur = true;
                        if (isRealTimeBlurEnabled()) {
                            invalidate();
                        }
                    }
                }

                float x = innerLayout.getLeft(), y = innerLayout.getTop();
                float w = innerLayout.getWidth(), h = innerLayout.getHeight();

                if (!transitionToDialog) {
                    canvas.save();
                    canvas.translate(-messageCell.sideStartX, -messageCell.sideStartY);
                    float cx = messageCell.getLeft() + messageCell.sideStartX - contentView.getTranslationX(), cy = y + AndroidUtilities.dp(VERTICAL_MARGIN_DP + AVATAR_DP + 24);
                    canvas.translate(cx, cy);
                    float pr;
                    if (arrowProgress < 0.5f) {
                        pr = arrowProgress * 2f;
                    } else {
                        pr = (1f - (arrowProgress - 0.5f) / 0.5f);
                    }
                    canvas.translate(0, -AndroidUtilities.dp(8) * pr);
                    messageCell.drawSideButton(canvas, -45 * pr);
                    canvas.restore();
                }

                y += innerLayout.getTranslationY();

                w = AndroidUtilities.lerp(AndroidUtilities.dp(32), w, showProgress);
                h = AndroidUtilities.lerp(AndroidUtilities.dp(32), h, MathUtils.clamp(showProgress, 0, 1));
                x = AndroidUtilities.lerp(messageCell.getLeft() + messageCell.sideStartX - contentView.getTranslationX(), x, Math.min(showProgress, 1) + (Math.max(showProgress, 1) - 1) / 2f);

                shadow.setAlpha((int) (innerLayout.getAlpha() * 0xFF));
                shadow.setBounds((int) (x - shadowPad.left), (int) (y - shadowPad.top), (int) (x + w + shadowPad.right), (int) (y + h + shadowPad.bottom));
                if (!GLGooeyView.drawingBlur) {
                    shadow.draw(canvas);

                    if (isFullyShown && wasSwipe) {
                        for (int i = 0; i < innerLayout.getChildCount(); i++) {
                            View ch = innerLayout.getChildAt(i);
                            float cx = innerLayout.getLeft() + ch.getX() + ch.getWidth() / 2f;
                            float progress = (ch.getScaleX() - 1f) / 0.1f * innerLayout.getAlpha();
                            float alpha = MathUtils.clamp(progress, 0, 1);

                            if (alpha != 0) {
                                if (tagLayouts[i] == null) {
                                    TLRPC.Dialog dialog = dialogs.get(i);
                                    TLRPC.User user = mActivity.getAccountInstance().getMessagesController().getUser(dialog.id);
                                    CharSequence name;
                                    if (UserObject.isUserSelf(user)) {
                                        name = LocaleController.getString(R.string.SavedMessages);
                                    } else {
                                        name = ContactsController.formatName(user);
                                    }
                                    int tagWidth = innerLayout.getWidth() + AndroidUtilities.dp(1);
                                    name = TextUtils.ellipsize(name, textPaint, tagWidth, TextUtils.TruncateAt.END);
                                    int realWidth = Math.round(textPaint.measureText(name, 0, name.length())) + AndroidUtilities.dp(1);
                                    tagLayouts[i] = new StaticLayout(name, 0, name.length(), textPaint, realWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                                }

                                StaticLayout layout = tagLayouts[i];
                                cx = MathUtils.clamp(cx, AndroidUtilities.dp(PADDING_DP) + layout.getWidth() / 2f, getWidth() - AndroidUtilities.dp(OUT_PADDING_DP) - layout.getWidth() / 2f);

                                canvas.save();
                                canvas.translate(0, y - innerLayout.getTranslationY() - AndroidUtilities.dp(12 + TAG_HEIGHT_DP));
                                float sc = AndroidUtilities.lerp(0.6f, 1f, progress);
                                canvas.scale(sc, sc, cx, AndroidUtilities.dp(TAG_HEIGHT_DP) / 2f);
                                AndroidUtilities.rectTmp.set(cx - layout.getWidth() / 2f - AndroidUtilities.dp(TAG_PADDING_DP), 0, cx + layout.getWidth() / 2f + AndroidUtilities.dp(TAG_PADDING_DP), AndroidUtilities.dp(TAG_HEIGHT_DP));
                                paint.setAlpha((int) (alpha * 0xFF));
                                canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(16), AndroidUtilities.dp(16), paint);
                                canvas.translate(cx - layout.getWidth() / 2f, (AndroidUtilities.rectTmp.height() - layout.getHeight()) / 2f);
                                int wasAlpha = textPaint.getAlpha();
                                textPaint.setAlpha((int) (wasAlpha * alpha));
                                layout.draw(canvas);
                                textPaint.setAlpha(wasAlpha);
                                canvas.restore();
                            }
                        }
                    }
                }
                AndroidUtilities.rectTmp.set(x, y, x + w, y + h);
                int alpha = bgPaint.getAlpha();
                bgPaint.setAlpha((int) (alpha * innerLayout.getAlpha()));
                canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(32), AndroidUtilities.dp(32), bgPaint);
                bgPaint.setAlpha(alpha);
            }
        };
        frameContentView.setWillNotDraw(false);
        frameContentView.setPadding(AndroidUtilities.dp(OUT_PADDING_DP), AndroidUtilities.dp(OUT_PADDING_DP), AndroidUtilities.dp(OUT_PADDING_DP), AndroidUtilities.dp(OUT_PADDING_DP));
        frameContentView.setClipToPadding(false);
        frameContentView.setClipChildren(false);

        innerLayout.setOrientation(LinearLayout.HORIZONTAL);
        innerLayout.setPadding(0, AndroidUtilities.dp(6), AndroidUtilities.dp(8), AndroidUtilities.dp(6));
        for (int i = 0; i < dialogs.size(); i++) {
            BackupImageView avatarImage = new BackupImageView(ctx);
            avatarImage.setRoundRadius(AndroidUtilities.dp(24));

            TLRPC.Dialog dialog = dialogs.get(i);
            loadAvatar(avatarImage, dialog);

            avatarImage.setScaleX(0);
            avatarImage.setScaleY(0);
            int finalI = i;
            avatarImage.setOnClickListener(v -> sendTo(finalI));
            innerLayout.addView(avatarImage, LayoutHelper.createLinear(AVATAR_DP, AVATAR_DP, 8, 0, 0, 0));
        }
        frameContentView.addView(innerLayout);

        int[] pos = new int[2];
        RecyclerListView listView = mActivity.getChatListView();
        listView.getLocationInWindow(pos);
        float listY = pos[1];
        mActivity.getContentView().getLocationInWindow(pos);
        float offsetY = listY - pos[1] - mActivity.getPullingDownOffset() - mActivity.getActionBar().getHeight();

        if (isRealTimeBlurEnabled()) {
            contentView = new GooeyView(mActivity.getContext()) {{
                getContainerView().addView(frameContentView);
            }};
        } else {
            contentView = frameContentView;
        }

        int w = AndroidUtilities.dp(AVATAR_DP + 8) * innerLayout.getChildCount() + AndroidUtilities.dp(8) + AndroidUtilities.dp(OUT_PADDING_DP) * 2;
        contentView.setTranslationX(MathUtils.clamp(messageCell.getLeft() + messageCell.sideStartX + AndroidUtilities.dp(16) - w / 2f, AndroidUtilities.dp(PADDING_DP - OUT_PADDING_DP), listView.getWidth() - w - AndroidUtilities.dp(PADDING_DP - OUT_PADDING_DP)));
        float childOffsetX = (messageCell.getLeft() + messageCell.sideStartX + AndroidUtilities.dp(16) - w / 2f) - (listView.getWidth() - w - AndroidUtilities.dp(PADDING_DP - OUT_PADDING_DP));
        contentView.setTranslationY(messageCell.getY() + messageCell.sideStartY + offsetY - AndroidUtilities.dp(VERTICAL_MARGIN_DP + AVATAR_DP + OUT_PADDING_DP + PADDING_DP * 2));
        float childOffsetY = AndroidUtilities.dp(VERTICAL_MARGIN_DP + AVATAR_DP + 24);

        overlayView = new FrameLayout(ctx);
        overlayView.setClipChildren(false);
        overlayView.setClipToPadding(false);
        overlayView.setOnClickListener(v1 -> {
            if (showProgress == 1) {
                dismiss();
            } else {
                onFullyShown = this::dismiss;
            }
        });
        overlayView.addView(contentView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) {{
            width = w;
        }});
        mActivity.getContentView().addView(overlayView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        new SpringAnimation(new FloatValueHolder(0))
                .setMinimumVisibleChange(1 / 512f)
                .setSpring(new SpringForce(1f)
                        .setStiffness(85f)
                        .setDampingRatio(0.45f))
                .addUpdateListener((animation, value, velocity) -> {
                    arrowProgress = value;
                    frameContentView.invalidate();
                })
                .start();

        new SpringAnimation(new FloatValueHolder(0))
                .setMinimumVisibleChange(1 / 512f)
                .setSpring(new SpringForce(1f)
                        .setStiffness(225f)
                        .setDampingRatio(0.8f))
                .addUpdateListener((animation, value, velocity) -> {
                    for (int i = 0; i < innerLayout.getChildCount(); i++) {
                        View ch = innerLayout.getChildAt(i);

                        float d = Math.abs(innerLayout.getChildCount() / 2f - (i + 0.5f));
                        float distance = 0.15f + d * 0.35f;
                        float p = (Math.max(value, distance) - distance) / (1f - distance);
                        ch.setScaleX(p);
                        ch.setScaleY(p);
                    }
                })
                .addEndListener((animation, canceled, value, velocity) -> {
                    isFullyShown = true;
                    if (onFullyShown != null) {
                        onFullyShown.run();
                    }
                })
                .start();
        new SpringAnimation(new FloatValueHolder(0))
                .setMinimumVisibleChange(1 / 512f)
                .setSpring(new SpringForce(1f)
                        .setStiffness(265f)
                        .setDampingRatio(0.75f))
                .addUpdateListener((animation, value, velocity) -> {
                    if (contentView instanceof GooeyView) {
                        innerLayout.setAlpha(MathUtils.clamp(value, 0, 0.05f) / 0.05f);
                        if (contentView.getHeight() != 0) {
                            ((GooeyView) contentView).setFade(AndroidUtilities.lerp(childOffsetY - AndroidUtilities.dp(32), contentView.getHeight(), value), contentView.getHeight());
                        }
                    } else {
                        innerLayout.setAlpha(MathUtils.clamp(value, 0, 0.15f) / 0.15f);
                    }
                    showProgress = value;

                    innerLayout.setTranslationX(childOffsetX * (1f - value));
                    innerLayout.setTranslationY(childOffsetY * MathUtils.clamp(1f - value, 0, 1));
                    frameContentView.invalidate();
                })
                .addEndListener((animation, canceled, value, velocity) -> {
                    if (contentView instanceof GooeyView) {
                        ((GooeyView) contentView).setSkipDraw(false);
                    }
                })
                .start();
    }

    private boolean isRealTimeBlurEnabled() {
        return LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR) && SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_HIGH;
    }

    private void loadAvatar(BackupImageView avatarImage, TLRPC.Dialog dialog) {
        TLRPC.User user = mActivity.getAccountInstance().getMessagesController().getUser(dialog.id);
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setInfo(mActivity.getCurrentAccount(), user);
        avatarDrawable.setScaleSize(0.85f);
        if (UserObject.isReplyUser(user)) {
            avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
            avatarImage.getImageReceiver().setImage(null, null, avatarDrawable, null, user, 0);
        } else if (UserObject.isAnonymous(user)) {
            avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_ANONYMOUS);
            avatarImage.getImageReceiver().setImage(null, null, avatarDrawable, null, user, 0);
        } else if (UserObject.isUserSelf(user)) {
            avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
            avatarImage.getImageReceiver().setImage(null, null, avatarDrawable, null, user, 0);
        } else {
            avatarImage.getImageReceiver().setForUserOrChat(user, avatarDrawable, null, true, VectorAvatarThumbDrawable.TYPE_SMALL, false);
        }
    }

    public void dismiss() {
        if (!isShown) return;

        isShown = false;
        ValueAnimator anim = ValueAnimator.ofFloat(0, 1).setDuration(150);
        anim.setInterpolator(CubicBezierInterpolator.DEFAULT);
        anim.addUpdateListener(animation -> {
            float val = (float) animation.getAnimatedValue();
            innerLayout.setAlpha(1f - val);
            innerLayout.setTranslationY(AndroidUtilities.dp(20) * val);
            frameContentView.invalidate();
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mActivity.getContentView().removeView(overlayView);
                if (blurBitmap != null) {
                    blurBitmap.recycle();
                    blurBitmap = null;
                    blurCanvas = null;
                }
            }
        });
        anim.start();
    }

    private void sendTo(int i) {
        if (!isShown) return;

        isShown = false;
        transitionToDialog = true;
        messageCell.invalidate();

        View ch = innerLayout.getChildAt(i);
        ch.setVisibility(View.INVISIBLE);
        float sc = ch.getScaleX();
        TLRPC.Dialog dialog = dialogs.get(i);

        int[] loc = new int[2], loc2 = new int[2];
        overlayView.getLocationInWindow(loc);
        ch.getLocationInWindow(loc2);

        float offsetX = loc2[0] - loc[0], offsetY = loc2[1] - loc[1];
        BackupImageView overlayAvatarView = new BackupImageView(contentView.getContext());
        overlayAvatarView.setRoundRadius(AndroidUtilities.dp(24));
        loadAvatar(overlayAvatarView, dialog);
        overlayView.addView(overlayAvatarView, LayoutHelper.createFrame(AVATAR_DP, AVATAR_DP));

        overlayAvatarView.setTranslationX(offsetX);
        overlayAvatarView.setTranslationY(offsetY);
        overlayAvatarView.setScaleX(sc);
        overlayAvatarView.setScaleY(sc);

        new SpringAnimation(new FloatValueHolder(0))
                .setMinimumVisibleChange(1 / 512f)
                .setSpring(new SpringForce(1f)
                        .setStiffness(1000f)
                        .setDampingRatio(1f))
                .addUpdateListener((animation, value, velocity) -> {
                    innerLayout.setAlpha(1f - value);
                    frameContentView.invalidate();

                    float scale = AndroidUtilities.lerp(sc, 0.5f, value);
                    overlayAvatarView.setScaleX(scale);
                    overlayAvatarView.setScaleY(scale);
                    overlayAvatarView.setTranslationX(AndroidUtilities.lerp(offsetX, AndroidUtilities.dp(21), value));

                    float tY;
                    if (value < 0.25f) {
                        tY = AndroidUtilities.lerp(offsetY, offsetY - AndroidUtilities.dp(32), value / 0.25f);
                    } else {
                        tY = AndroidUtilities.lerp(offsetY - AndroidUtilities.dp(32), mActivity.getChatActivityEnterView().getTop() - overlayView.getTop() - AndroidUtilities.dp(AVATAR_DP + 12),  (value - 0.25f) / 0.75f);
                    }
                    overlayAvatarView.setTranslationY(tY);
                })
                .addEndListener((animation, canceled, value, velocity) -> {
                    mActivity.undoView.leftImageView.animate().cancel();
                    mActivity.undoView.leftImageView.animate().alpha(1).setDuration(150)
                            .scaleX(1f).scaleY(1)
                            .setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                    mActivity.undoView.leftImageView.playAnimation();

                    overlayAvatarView.animate().cancel();
                    overlayAvatarView.animate().alpha(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mActivity.getContentView().removeView(overlayView);
                            if (blurBitmap != null) {
                                blurBitmap.recycle();
                                blurBitmap = null;
                                blurCanvas = null;
                            }
                        }
                    }).start();
                })
                .start();

        ArrayList<MessageObject> list = messageCell.getCurrentMessagesGroup() != null ? messageCell.getCurrentMessagesGroup().messages : new ArrayList<>(Collections.singletonList(messageCell.getMessageObject()));
        mActivity.createUndoView();
        if (mActivity.undoView != null) {
            mActivity.undoView.showWithAction(dialog.id, UndoView.ACTION_FWD_MESSAGES, list.size(), this, null, null);
        }

        mActivity.getAccountInstance().getSendMessagesHelper().sendMessage(list, dialog.id, false, false, true, 0);
    }

    public boolean isTransitioning() {
        return transitionToDialog;
    }

    public void onTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_MOVE && isFullyShown && canStartSwipe) {
            if (Math.sqrt(Math.pow(ev.getX() - startX, 2) + Math.pow(ev.getY() - startY, 2)) > touchSlop) {
                isSwiping = true;
            }

            if (isSwiping) {
                float bX = contentView.getTranslationX() + innerLayout.getX() - messageCell.getLeft();
                int j = -1;
                for (int i = 0; i < innerLayout.getChildCount(); i++) {
                    View ch = innerLayout.getChildAt(i);
                    if (ev.getX() >= bX + ch.getX() - AndroidUtilities.dp(4) && ev.getX() <= bX + ch.getX() + ch.getWidth() + AndroidUtilities.dp(4)) {
                        j = i;
                        break;
                    }
                }

                if (selectedIndex != j) {
                    selectedIndex = j;
                    wasSwipe = true;
                    if (j != -1) {
                        try {
                            messageCell.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                        } catch (Exception ignored) {}
                    }

                    for (int i = 0; i < innerLayout.getChildCount(); i++) {
                        View ch = innerLayout.getChildAt(i);
                        boolean selected = j == i;

                        int finalI = i;
                        AndroidUtilities.runOnUIThread(()->{
                            if (selected && selectedIndex != finalI) return;

                            ch.animate().cancel();
                            ch.animate().alpha(selected ? 1 : 0.6f).setDuration(150).start();

                            SpringAnimation anim = (SpringAnimation) ch.getTag();
                            if (anim != null) {
                                anim.cancel();
                            }
                            anim = new SpringAnimation(new FloatValueHolder(ch.getScaleX()))
                                    .setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE)
                                    .setSpring(new SpringForce(selected ? 1.1f : 1f)
                                            .setStiffness(500f)
                                            .setDampingRatio(0.75f))
                                    .addUpdateListener((animation, value, velocity) -> {
                                        ch.setScaleX(value);
                                        ch.setScaleY(value);
                                    })
                                    .addEndListener((animation, canceled, value, velocity) -> ch.setTag(null));
                            ch.setTag(anim);
                            anim.start();
                        }, selected ? 10 : 0);
                    }
                    frameContentView.invalidate();
                }
            }
        }
        boolean wasSwipe = isSwiping;
        if (ev.getActionMasked() == MotionEvent.ACTION_UP || ev.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            canStartSwipe = false;
            isSwiping = false;
        }

        if (ev.getActionMasked() == MotionEvent.ACTION_UP) {
            if (wasSwipe) {
                if (selectedIndex != -1) {
                    sendTo(selectedIndex);
                    selectedIndex = -1;
                } else {
                    dismiss();
                }
            }
        }
    }

    public ChatMessageCell getCell() {
        return messageCell;
    }

    public boolean isShown() {
        return isShown;
    }
}
