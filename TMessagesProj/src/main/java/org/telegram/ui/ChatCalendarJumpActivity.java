package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextPaint;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.GestureDetectorCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.ChatActivityActionsPreviewView;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UndoView;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;

public class ChatCalendarJumpActivity extends BaseFragment {
    private ChatActivityActionsPreviewView actionsPreviewView;
    private ChatActivity.ThemeDelegate themeDelegate;
    private UndoView undoView;
    private int dateSelectedStart, dateSelectedEnd;
    private Paint selectOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint selectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RectF tempRect = new RectF();
    private View blurredView;

    private int mMinDate;
    private boolean wasSelected;
    private boolean isInForceSelectMode;

    private NumberTextView selectedDaysCountTextView;
    private TextView selectedDaysTitle;

    private ValueAnimator selectedDaysCounterAnimator;

    private ValueAnimator selectionAnimator;
    private TextView bottomBtn;

    private CalendarAdapter adapter;
    private Callback callback;
    private RecyclerListView listView;

    private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private TextPaint activeTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private TextPaint textPaintHeader = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private Paint blackoutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private long dialogId;
    private boolean loading;
    private boolean checkEnterItems;

    int startFromYear;
    int startFromMonth;
    int monthCount;

    SparseArray<SparseArray<PeriodDay>> messagesByYearMonth = new SparseArray<>();
    boolean endReached;
    int startOffset = 0;
    int lastId;
    int minMontYear;
    private boolean isOpened;
    int selectedYear;
    int selectedMonth;

    public ChatCalendarJumpActivity(Bundle args, int selectedDate, ChatActivity.ThemeDelegate themeDelegate) {
        super(args);
        this.themeDelegate = themeDelegate;

        if (selectedDate != 0) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(selectedDate * 1000L);
            selectedYear = calendar.get(Calendar.YEAR);
            selectedMonth = calendar.get(Calendar.MONTH);
        }
        selectOutlinePaint.setStyle(Paint.Style.STROKE);
        selectOutlinePaint.setStrokeCap(Paint.Cap.ROUND);
        selectOutlinePaint.setStrokeWidth(AndroidUtilities.dp(2));
    }

    @Override
    public boolean onFragmentCreate() {
        dialogId = getArguments().getLong("dialog_id");
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        textPaint.setTextSize(AndroidUtilities.dp(16));
        textPaint.setTextAlign(Paint.Align.CENTER);

        textPaintHeader.setTextSize(AndroidUtilities.dp(11));
        textPaintHeader.setTextAlign(Paint.Align.CENTER);
        textPaintHeader.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

        activeTextPaint.setTextSize(AndroidUtilities.dp(16));
        activeTextPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        activeTextPaint.setTextAlign(Paint.Align.CENTER);

        FrameLayout contentView = new FrameLayout(context);
        createActionBar(context);
        contentView.addView(actionBar);
        actionBar.setCastShadows(false);

        listView = new RecyclerListView(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                checkEnterItems = false;
            }
        };
        LinearLayoutManager layoutManager;
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context));
        layoutManager.setReverseLayout(true);
        listView.setAdapter(adapter = new CalendarAdapter());
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                checkLoadNext();
            }
        });

        FrameLayout lFrame = new FrameLayout(context);
        lFrame.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        View bottomShadowView = new View(context);
        Drawable bottomShadow = ContextCompat.getDrawable(context, R.drawable.header_shadow).mutate();
        bottomShadowView.setBackground(bottomShadow);
        bottomShadowView.setRotation(180);
        lFrame.addView(bottomShadowView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.BOTTOM));

        LinearLayout ll = new LinearLayout(context);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.addView(lFrame, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        bottomBtn = new TextView(context);
        bottomBtn.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
        bottomBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        bottomBtn.setAllCaps(true);
        bottomBtn.setGravity(Gravity.CENTER);
        bottomBtn.setBackground(Theme.getSelectorDrawable(true));
        bottomBtn.setOnClickListener(v -> {
            if (!isInForceSelectMode && dateSelectedStart == 0 && dateSelectedEnd == 0) {
                isInForceSelectMode = true;
                updateTitleAndButton();
                return;
            }
            showClearHistory(context, dateSelectedStart, dateSelectedEnd + 86400);
        });
        ll.addView(bottomBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52));

        contentView.addView(ll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 0, 36, 0, 0));

        final String[] daysOfWeek = new String[]{
                LocaleController.getString("CalendarWeekNameShortMonday", R.string.CalendarWeekNameShortMonday),
                LocaleController.getString("CalendarWeekNameShortTuesday", R.string.CalendarWeekNameShortTuesday),
                LocaleController.getString("CalendarWeekNameShortWednesday", R.string.CalendarWeekNameShortWednesday),
                LocaleController.getString("CalendarWeekNameShortThursday", R.string.CalendarWeekNameShortThursday),
                LocaleController.getString("CalendarWeekNameShortFriday", R.string.CalendarWeekNameShortFriday),
                LocaleController.getString("CalendarWeekNameShortSaturday", R.string.CalendarWeekNameShortSaturday),
                LocaleController.getString("CalendarWeekNameShortSunday", R.string.CalendarWeekNameShortSunday),
        };

        Drawable headerShadowDrawable = ContextCompat.getDrawable(context, R.drawable.header_shadow).mutate();

        View calendarSignatureView = new View(context) {

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float xStep = getMeasuredWidth() / 7f;
                for (int i = 0; i < 7; i++) {
                    float cx = xStep * i + xStep / 2f;
                    float cy = (getMeasuredHeight() - AndroidUtilities.dp(2)) / 2f;
                    canvas.drawText(daysOfWeek[i], cx, cy + AndroidUtilities.dp(5), textPaintHeader);
                }
                headerShadowDrawable.setBounds(0, getMeasuredHeight() - AndroidUtilities.dp(3), getMeasuredWidth(), getMeasuredHeight());
                headerShadowDrawable.draw(canvas);
            }
        };

        contentView.addView(calendarSignatureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, 0, 0, 0, 0, 0));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        actionBar.setTitle(LocaleController.getString("Calendar", R.string.Calendar));

        undoView = new UndoView(context, this, false, themeDelegate);
        undoView.setVisibility(View.GONE);
        contentView.addView(undoView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 8, 8, 8, 8));

        blurredView = new View(context) {
            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                if (fragmentView != null) {
                    fragmentView.invalidate();
                }
            }
        };
        blurredView.setVisibility(View.GONE);

        fragmentView = contentView;

        Calendar calendar = Calendar.getInstance();
        startFromYear = calendar.get(Calendar.YEAR);
        startFromMonth = calendar.get(Calendar.MONTH);

        if (selectedYear != 0) {
            monthCount = (startFromYear - selectedYear) * 12 + startFromMonth - selectedMonth + 1;
            layoutManager.scrollToPositionWithOffset(monthCount - 1, AndroidUtilities.dp(120));
        }
        if (monthCount < 3) {
            monthCount = 3;
        }

        loadNext();

        actionsPreviewView = new ChatActivityActionsPreviewView(context);

        actionBar.setBackButtonDrawable(new BackDrawable(false));

        ActionBarMenu actionMode = actionBar.createActionMode();

        FrameLayout fl = new FrameLayout(actionMode.getContext());

        selectedDaysCountTextView = new NumberTextView(actionMode.getContext());
        selectedDaysCountTextView.setTextSize(18);
        selectedDaysCountTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        selectedDaysCountTextView.setAlpha(0);
        fl.addView(selectedDaysCountTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL));
        selectedDaysCountTextView.setOnTouchListener((v, event) -> true);

        selectedDaysTitle = new TextView(actionMode.getContext());
        selectedDaysTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        selectedDaysTitle.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        selectedDaysTitle.setGravity(Gravity.CENTER_VERTICAL);
        fl.addView(selectedDaysTitle, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL));

        selectedDaysCountTextView.setOnTextWidthProgressChangedListener((fromWidth, toWidth, progress) -> onAnimateSelectedDaysTitle(selectedDaysCountTextView.getAlpha(), fromWidth, toWidth, progress));

        actionMode.addView(fl, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f, 65, 0, 0, 0));

        updateColors();
        activeTextPaint.setColor(Color.WHITE);

        updateTitleAndButton(true);

        return fragmentView;
    }

    private void onAnimateSelectedDaysTitle(float alpha, float fromWidth, float toWidth, float progress) {
        selectedDaysTitle.setTranslationX(AndroidUtilities.dp(8) + (fromWidth + (toWidth - fromWidth) * (1f - Math.abs(progress))) * alpha);
    }

    private void updateTitleAndButton() {
        updateTitleAndButton(false);
    }

    private void updateTitleAndButton(boolean force) {
        boolean selected = dateSelectedStart != 0 || dateSelectedEnd != 0 || isInForceSelectMode;
        int daysCount = dateSelectedEnd / 86400 - dateSelectedStart / 86400 + 1;
        float counterAlpha;
        if (isInForceSelectMode) {
            selectedDaysTitle.setText(LocaleController.getString("SelectDays", R.string.SelectDays));
            counterAlpha = 0;
        } else {
            selectedDaysTitle.setText(LocaleController.getPluralString("DaysSimple", daysCount));
            selectedDaysCountTextView.setNumber(daysCount, true);
            counterAlpha = 1;
        }
        if (selectedDaysCounterAnimator != null)
            selectedDaysCounterAnimator.cancel();
        if (selectedDaysCountTextView.getAlpha() != counterAlpha) {
            ValueAnimator anim = ValueAnimator.ofFloat(selectedDaysCountTextView.getAlpha(), counterAlpha)
                    .setDuration(150);
            anim.addUpdateListener(animation -> {
                float v = (float) animation.getAnimatedValue();
                selectedDaysCountTextView.setAlpha(v);
                onAnimateSelectedDaysTitle(v, selectedDaysCountTextView.getOldTextWidth(), selectedDaysCountTextView.getTextWidth(), selectedDaysCountTextView.getProgress());
            });
            anim.start();
            selectedDaysCounterAnimator = anim;
        }

        if (selected != wasSelected || force) {
            if (selected) {
                if (daysCount == 0) {
                    bottomBtn.animate().cancel();
                    bottomBtn.animate().alpha(0.5f).setDuration(150).start();
                    bottomBtn.setTypeface(Typeface.DEFAULT);
                    bottomBtn.setClickable(false);
                } else {
                    bottomBtn.animate().cancel();
                    bottomBtn.animate().alpha(1).setDuration(150).start();
                    bottomBtn.setClickable(true);
                    bottomBtn.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"), Typeface.BOLD);
                }
                bottomBtn.setText(LocaleController.getString("ClearHistory", R.string.ClearHistory));
                bottomBtn.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
                actionBar.showActionMode(true);
            } else {
                bottomBtn.animate().cancel();
                bottomBtn.animate().alpha(1).setDuration(150).start();
                bottomBtn.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"), Typeface.BOLD);
                bottomBtn.setClickable(true);
                bottomBtn.setText(LocaleController.getString("SelectDays", R.string.SelectDays));
                bottomBtn.setTextColor(Theme.getColor(Theme.key_chat_messagePanelVoiceBackground));

                actionBar.hideActionMode();
            }
            wasSelected = selected;
        }
    }

    private void updateColors() {
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        activeTextPaint.setColor(Color.WHITE);
        textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textPaintHeader.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setTitleColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
        selectedDaysTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        selectedDaysCountTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_listSelector), false);
    }

    private void loadNext() {
        if (loading || endReached) {
            return;
        }
        loading = true;
        TLRPC.TL_messages_getSearchResultsCalendar req = new TLRPC.TL_messages_getSearchResultsCalendar();
        req.filter = new TLRPC.TL_inputMessagesFilterPhotoVideo();
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        req.offset_id = lastId;

        Calendar calendar = Calendar.getInstance();
        listView.setItemAnimator(null);
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                TLRPC.TL_messages_searchResultsCalendar res = (TLRPC.TL_messages_searchResultsCalendar) response;

                int maxDate = (int) (System.currentTimeMillis() / 1000L) + 86400;
                for (int i = 0; i < res.periods.size(); i++) {
                    TLRPC.TL_searchResultsCalendarPeriod period = res.periods.get(i);
                    calendar.setTimeInMillis(period.date * 1000L);
                    calendar.set(Calendar.HOUR_OF_DAY, 0);
                    calendar.set(Calendar.MINUTE, 0);
                    calendar.set(Calendar.SECOND, 0);
                    calendar.set(Calendar.MILLISECOND, 0);

                    int month = calendar.get(Calendar.YEAR) * 100 + calendar.get(Calendar.MONTH);
                    SparseArray<PeriodDay> messagesByDays = messagesByYearMonth.get(month);
                    if (messagesByDays == null) {
                        messagesByDays = new SparseArray<>();
                        messagesByYearMonth.put(month, messagesByDays);
                    }
                    PeriodDay periodDay = new PeriodDay();
                    MessageObject messageObject = new MessageObject(currentAccount, res.messages.get(i), false, false);
                    periodDay.messageObject = messageObject;
                    startOffset += res.periods.get(i).count;
                    periodDay.startOffset = startOffset;
                    periodDay.date = (int) (calendar.getTimeInMillis() / 1000L);
                    int index = calendar.get(Calendar.DAY_OF_MONTH) - 1;
                    if (messagesByDays.get(index, null) == null) {
                        messagesByDays.put(index, periodDay);
                    }
                    if (month < minMontYear || minMontYear == 0) {
                        minMontYear = month;
                    }
                }

                int minDate;
                loading = false;
                if (!res.messages.isEmpty()) {
                    lastId = res.messages.get(res.messages.size() - 1).id;
                    endReached = false;
                    checkLoadNext();
                    int d = res.messages.get(res.messages.size() - 1).date;
                    calendar.setTimeInMillis(d * 1000L);
                    calendar.set(Calendar.HOUR_OF_DAY, 0);
                    calendar.set(Calendar.MINUTE, 0);
                    calendar.set(Calendar.SECOND, 0);
                    calendar.set(Calendar.MILLISECOND, 0);
                    d = (int) (calendar.getTimeInMillis() / 1000L);
                    minDate = d;
                } else {
                    endReached = true;
                    minDate = res.min_date;
                    mMinDate = res.min_date;
                }
                for (int date = minDate; date < maxDate; date += 86400) {
                    calendar.setTimeInMillis(date * 1000L);
                    calendar.set(Calendar.HOUR_OF_DAY, 0);
                    calendar.set(Calendar.MINUTE, 0);
                    calendar.set(Calendar.SECOND, 0);
                    calendar.set(Calendar.MILLISECOND, 0);

                    int month = calendar.get(Calendar.YEAR) * 100 + calendar.get(Calendar.MONTH);
                    SparseArray<PeriodDay> messagesByDays = messagesByYearMonth.get(month);
                    if (messagesByDays == null) {
                        messagesByDays = new SparseArray<>();
                        messagesByYearMonth.put(month, messagesByDays);
                    }
                    PeriodDay periodDay = new PeriodDay();
                    periodDay.hasImage = false;
                    periodDay.date = (int) (calendar.getTimeInMillis() / 1000L);
                    int index = calendar.get(Calendar.DAY_OF_MONTH) - 1;
                    if (messagesByDays.get(index, null) == null) {
                        messagesByDays.put(index, periodDay);
                    }
                }

                if (isOpened) {
                    checkEnterItems = true;
                }
                listView.invalidate();
                int newMonthCount = (int) (((calendar.getTimeInMillis() / 1000) - res.min_date) / 2629800) + 1;
                adapter.notifyItemRangeChanged(0, monthCount);
                if (newMonthCount > monthCount) {
                    adapter.notifyItemRangeInserted(monthCount + 1, newMonthCount);
                    monthCount = newMonthCount;
                }
                if (endReached) {
                    resumeDelayedFragmentAnimation();
                }
            }
        }));
    }

    private void checkLoadNext() {
        if (loading || endReached) {
            return;
        }
        int listMinMonth = Integer.MAX_VALUE;
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child instanceof MonthView) {
                int currentMonth = ((MonthView) child).currentYear * 100 + ((MonthView) child).currentMonthInYear;
                if (currentMonth < listMinMonth) {
                    listMinMonth = currentMonth;
                }
            }
        };
        int min1 = (minMontYear / 100 * 12) + minMontYear % 100;
        int min2 = (listMinMonth / 100 * 12) + listMinMonth % 100;
        if (min1 + 3 >= min2) {
            loadNext();
        }
    }

    private class CalendarAdapter extends RecyclerView.Adapter {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new MonthView(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            MonthView monthView = (MonthView) holder.itemView;

            int year = startFromYear - position / 12;
            int month = startFromMonth - position % 12;
            if (month < 0) {
                month += 12;
                year--;
            }
            boolean animated = monthView.currentYear == year && monthView.currentMonthInYear == month;
            monthView.setDate(year, month, messagesByYearMonth.get(year * 100 + month), animated);
        }

        @Override
        public long getItemId(int position) {
            int year = startFromYear - position / 12;
            int month = startFromMonth - position % 12;
            return year * 100L + month;
        }

        @Override
        public int getItemCount() {
            return monthCount;
        }
    }

    private class MonthView extends FrameLayout {

        SimpleTextView titleView;
        int currentYear;
        int currentMonthInYear;
        int daysInMonth;
        int startDayOfWeek;
        int cellCount;
        int startMonthTime;

        SparseArray<PeriodDay> messagesByDays = new SparseArray<>();
        SparseArray<ImageReceiver> imagesByDays = new SparseArray<>();

        boolean attached;

        GestureDetectorCompat gestureDetector;

        public MonthView(Context context) {
            super(context);
            setWillNotDraw(false);
            titleView = new SimpleTextView(context);
            titleView.setTextSize(15);
            titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleView.setGravity(Gravity.CENTER);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 28, 0, 0, 12, 0, 4));

            gestureDetector = new GestureDetectorCompat(context, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(MotionEvent e) {
                    return true;
                }

                @SuppressLint("NotifyDataSetChanged")
                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    float pressedX = e.getX(), pressedY = e.getY();
                    if (messagesByDays != null) {
                        int currentCell = 0;
                        int currentColumn = startDayOfWeek;

                        float xStep = getMeasuredWidth() / 7f;
                        float yStep = AndroidUtilities.dp(44 + 8);
                        int hrad = AndroidUtilities.dp(44) / 2;
                        for (int i = 0; i < daysInMonth; i++) {
                            float cx = xStep * currentColumn + xStep / 2f;
                            float cy = yStep * currentCell + yStep / 2f + AndroidUtilities.dp(44);

                            if (pressedX >= cx - hrad && pressedX <= cx + hrad && pressedY >= cy - hrad && pressedY <= cy + hrad) {
                                PeriodDay day = messagesByDays.get(i, null);
                                if (day != null) {
                                    if (selectionAnimator != null) {
                                        selectionAnimator.cancel();
                                        selectionAnimator = null;
                                    }
                                    if (dateSelectedStart != 0 || dateSelectedEnd != 0 || isInForceSelectMode) {
                                        if (dateSelectedStart == day.date && dateSelectedEnd == day.date) {
                                            dateSelectedStart = dateSelectedEnd = 0;
                                            animateSelection();
                                            return true;
                                        }
                                        if (dateSelectedStart == day.date) {
                                            dateSelectedStart = dateSelectedEnd;
                                            animateSelection();
                                            return true;
                                        }
                                        if (dateSelectedEnd == day.date) {
                                            dateSelectedEnd = dateSelectedStart;
                                            animateSelection();
                                            return true;
                                        }
                                        if (!isInForceSelectMode && dateSelectedStart == dateSelectedEnd) {
                                            if (day.date > dateSelectedEnd) {
                                                dateSelectedEnd = day.date;
                                            } else {
                                                dateSelectedStart = day.date;
                                            }
                                            animateSelection();
                                            return true;
                                        }

                                        dateSelectedStart = dateSelectedEnd = day.date;
                                        if (isInForceSelectMode) isInForceSelectMode = false;
                                        animateSelection();
                                        return true;
                                    }

                                    callback.onDateSelected(day.date);
                                    finishFragment();
                                    return true;
                                }
                            }

                            currentColumn++;
                            if (currentColumn >= 7) {
                                currentColumn = 0;
                                currentCell++;
                            }
                        }
                    }
                    return false;
                }

                @SuppressLint("NotifyDataSetChanged")
                @Override
                public void onLongPress(MotionEvent e) {
                    float pressedX = e.getX(), pressedY = e.getY();
                    if (messagesByDays != null) {
                        int currentCell = 0;
                        int currentColumn = startDayOfWeek;

                        float xStep = getMeasuredWidth() / 7f;
                        float yStep = AndroidUtilities.dp(44 + 8);
                        int hrad = AndroidUtilities.dp(44) / 2;
                        for (int i = 0; i < daysInMonth; i++) {
                            float cx = xStep * currentColumn + xStep / 2f;
                            float cy = yStep * currentCell + yStep / 2f + AndroidUtilities.dp(44);

                            if (pressedX >= cx - hrad && pressedX <= cx + hrad && pressedY >= cy - hrad && pressedY <= cy + hrad) {
                                PeriodDay day = messagesByDays.get(i, null);
                                if (day != null) {
                                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);

                                    if (dateSelectedStart != 0 || dateSelectedEnd != 0) {
                                        onSingleTapUp(e);
                                        return;
                                    }

                                    Bundle args = new Bundle();
                                    args.putLong("user_id", getArguments().getLong("dialog_id"));
                                    args.putInt("date_only_date", day.date);
                                    args.putBoolean("scrollToTopOnResume", true);
                                    args.putBoolean("need_remove_previous_same_chat_activity", false);

                                    ChatActivity c = new ChatActivity(args);
                                    c.onFragmentCreate();
                                    c.setInPreviewMode(true);
                                    c.setInBubbleMode(inBubbleMode);
                                    c.setParentFragment(ChatCalendarJumpActivity.this);

                                    actionsPreviewView.removeAllViews();
                                    prepareBlurBitmap();
                                    actionsPreviewView.setActions(Arrays.asList(
                                            new ChatActivityActionsPreviewView.Action(0, R.drawable.msg_message, LocaleController.getString("JumpToDate", R.string.JumpToDate)),
                                            new ChatActivityActionsPreviewView.Action(1, R.drawable.msg_select, LocaleController.getString("SelectThisDay", R.string.SelectThisDay)),
                                            new ChatActivityActionsPreviewView.Action(2, R.drawable.msg_delete, LocaleController.getString("ClearHistory", R.string.ClearHistory))
                                    ), action -> {
                                        switch (action.id) {
                                            case 0:
                                                callback.onDateSelected(day.date);
                                                finishFragment();
                                                break;
                                            case 1:
                                                if (selectionAnimator != null) selectionAnimator.cancel();
                                                dateSelectedStart = dateSelectedEnd = day.date;
                                                animateSelection();
                                                break;
                                            case 2:
                                                showClearHistory(context, day.date, day.date + 86400);
                                                break;
                                        }
                                    });
                                    actionsPreviewView.setChatActivity(c);

                                    FrameLayout fl = getParentLayout();
                                    actionsPreviewView.setOnDismissListener(v -> {
                                        fl.removeView(v);
                                        blurredView.setVisibility(GONE);
                                        actionsPreviewView.setBlurredView(null);
                                    });
                                    fl.addView(actionsPreviewView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

                                    actionsPreviewView.show();

                                    break;
                                }
                            }

                            currentColumn++;
                            if (currentColumn >= 7) {
                                currentColumn = 0;
                                currentCell++;
                            }
                        }
                    }
                }
            });
            gestureDetector.setIsLongpressEnabled(true);
        }

        private void startSelectionAnimation(int fromDate, int toDate) {
            if (messagesByDays != null) {
                for (int i = 0; i < daysInMonth; i++) {
                    PeriodDay day = messagesByDays.get(i, null);
                    if (day != null) {
                        day.fromSelProgress = day.selectProgress;
                        day.toSelProgress = day.date >= fromDate && day.date <= toDate ? 1 : 0;

                        day.fromSelSEProgress = day.selectStartEndProgress;
                        if (day.date == fromDate || day.date == toDate)
                            day.toSelSEProgress = 1;
                        else day.toSelSEProgress = 0;
                    }
                }
            }
        }

        private void setSelectionValue(float f) {
            if (messagesByDays != null) {
                for (int i = 0; i < daysInMonth; i++) {
                    PeriodDay day = messagesByDays.get(i, null);
                    if (day != null) {
                        day.selectProgress = day.fromSelProgress + (day.toSelProgress - day.fromSelProgress) * f;
                        day.selectStartEndProgress = day.fromSelSEProgress + (day.toSelSEProgress - day.fromSelSEProgress) * f;
                    }
                }
            }
            invalidate();
        }

        private SparseArray<ValueAnimator> rowAnimators = new SparseArray<>();
        private SparseArray<RowAnimationValue> rowSelectionPos = new SparseArray<>();

        public void dismissRowAnimations(boolean animate) {
            for (int i = 0; i < rowSelectionPos.size(); i++) {
                animateRow(rowSelectionPos.keyAt(i), 0, 0, false, animate);
            }
        }

        /**
         * Animates row selection
         * @param row Row to animate
         * @param startColumn Start column to animate
         * @param endColumn End column to animate
         * @param appear If we should animate appear animation
         */
        public void animateRow(int row, int startColumn, int endColumn, boolean appear, boolean animate) {
            ValueAnimator a = rowAnimators.get(row);
            if (a != null) a.cancel();

            float xStep = getMeasuredWidth() / 7f;

            float cxFrom1, cxFrom2, fromAlpha;
            RowAnimationValue p = rowSelectionPos.get(row);
            if (p != null) {
                cxFrom1 = p.startX;
                cxFrom2 = p.endX;
                fromAlpha = p.alpha;
            } else {
                cxFrom1 = xStep * startColumn + xStep / 2f;
                cxFrom2 = xStep * startColumn + xStep / 2f;
                fromAlpha = 0;
            }
            float cxTo1 = appear ? xStep * startColumn + xStep / 2f : cxFrom1;
            float cxTo2 = appear ? xStep * endColumn + xStep / 2f : cxFrom2;
            float toAlpha = appear ? 1 : 0;

            RowAnimationValue pr = new RowAnimationValue(cxFrom1, cxFrom2, fromAlpha);
            rowSelectionPos.put(row, pr);

            if (animate) {
                ValueAnimator anim = ValueAnimator.ofFloat(0, 1).setDuration(300);
                anim.setInterpolator(Easings.easeInOutQuad);
                anim.addUpdateListener(animation -> {
                    float val = (float) animation.getAnimatedValue();
                    pr.startX = cxFrom1 + (cxTo1 - cxFrom1) * val;
                    pr.endX = cxFrom2 + (cxTo2 - cxFrom2) * val;
                    pr.alpha = fromAlpha + (toAlpha - fromAlpha) * val;
                    invalidate();
                });
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationCancel(Animator animation) {
                        pr.startX = cxTo1;
                        pr.endX = cxTo2;
                        pr.alpha = toAlpha;
                        invalidate();
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        rowAnimators.remove(row);
                        if (!appear)
                            rowSelectionPos.remove(row);
                    }
                });
                anim.start();
                rowAnimators.put(row, anim);
            } else {
                pr.startX = cxTo1;
                pr.endX = cxTo2;
                pr.alpha = toAlpha;
                invalidate();
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return gestureDetector.onTouchEvent(event);
        }

        public void setDate(int year, int monthInYear, SparseArray<PeriodDay> messagesByDays, boolean animated) {
            boolean dateChanged = year != currentYear && monthInYear != currentMonthInYear;
            currentYear = year;
            currentMonthInYear = monthInYear;
            this.messagesByDays = messagesByDays;

            if (dateChanged) {
                if (imagesByDays != null) {
                    for (int i = 0; i < imagesByDays.size(); i++) {
                        imagesByDays.valueAt(i).onDetachedFromWindow();
                        imagesByDays.valueAt(i).setParentView(null);
                    }
                    imagesByDays = null;
                }
            }
            if (messagesByDays != null) {
                if (imagesByDays == null) {
                    imagesByDays = new SparseArray<>();
                }

                for (int i = 0; i < daysInMonth; i++) {
                    PeriodDay day = messagesByDays.get(i, null);
                    if (day != null) {
                        day.selectProgress = day.date >= dateSelectedStart && day.date <= dateSelectedEnd ? 1 : 0;

                        if (day.date == dateSelectedStart || day.date == dateSelectedEnd)
                            day.selectStartEndProgress = 1;
                        else day.selectStartEndProgress = 0;
                    }
                }

                for (int i = 0; i < messagesByDays.size(); i++) {
                    int key = messagesByDays.keyAt(i);
                    if (imagesByDays.get(key, null) != null) {
                        continue;
                    }
                    ImageReceiver receiver = new ImageReceiver();
                    receiver.setParentView(this);
                    PeriodDay periodDay = messagesByDays.get(key);
                    MessageObject messageObject = periodDay.messageObject;
                    if (messageObject != null) {
                        if (messageObject.isVideo()) {
                            TLRPC.Document document = messageObject.getDocument();
                            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 50);
                            TLRPC.PhotoSize qualityThumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 320);
                            if (thumb == qualityThumb) {
                                qualityThumb = null;
                            }
                            if (thumb != null) {
                                if (messageObject.strippedThumb != null) {
                                    receiver.setImage(ImageLocation.getForDocument(qualityThumb, document), "44_44", messageObject.strippedThumb, null, messageObject, 0);
                                } else {
                                    receiver.setImage(ImageLocation.getForDocument(qualityThumb, document), "44_44", ImageLocation.getForDocument(thumb, document), "b", (String) null, messageObject, 0);
                                }
                            }
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto && messageObject.messageOwner.media.photo != null && !messageObject.photoThumbs.isEmpty()) {
                            TLRPC.PhotoSize currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 50);
                            TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 320, false, currentPhotoObjectThumb, false);
                            if (messageObject.mediaExists || DownloadController.getInstance(currentAccount).canDownloadMedia(messageObject)) {
                                if (currentPhotoObject == currentPhotoObjectThumb) {
                                    currentPhotoObjectThumb = null;
                                }
                                if (messageObject.strippedThumb != null) {
                                    receiver.setImage(ImageLocation.getForObject(currentPhotoObject, messageObject.photoThumbsObject), "44_44", null, null, messageObject.strippedThumb, currentPhotoObject != null ? currentPhotoObject.size : 0, null, messageObject, messageObject.shouldEncryptPhotoOrVideo() ? 2 : 1);
                                } else {
                                    receiver.setImage(ImageLocation.getForObject(currentPhotoObject, messageObject.photoThumbsObject), "44_44", ImageLocation.getForObject(currentPhotoObjectThumb, messageObject.photoThumbsObject), "b", currentPhotoObject != null ? currentPhotoObject.size : 0, null, messageObject, messageObject.shouldEncryptPhotoOrVideo() ? 2 : 1);
                                }
                            } else {
                                if (messageObject.strippedThumb != null) {
                                    receiver.setImage(null, null, messageObject.strippedThumb, null, messageObject, 0);
                                } else {
                                    receiver.setImage(null, null, ImageLocation.getForObject(currentPhotoObjectThumb, messageObject.photoThumbsObject), "b", (String) null, messageObject, 0);
                                }
                            }
                        }
                        receiver.setRoundRadius(AndroidUtilities.dp(22));
                        imagesByDays.put(key, receiver);
                    }
                }
            }

            YearMonth yearMonthObject = YearMonth.of(year, monthInYear + 1);
            daysInMonth = yearMonthObject.lengthOfMonth();

            Calendar calendar = Calendar.getInstance();
            calendar.set(year, monthInYear, 0);
            startDayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 6) % 7;
            startMonthTime= (int) (calendar.getTimeInMillis() / 1000L);

            int totalColumns = daysInMonth + startDayOfWeek;
            cellCount = (int) (totalColumns / 7f) + (totalColumns % 7 == 0 ? 0 : 1);
            calendar.set(year, monthInYear + 1, 0);
            titleView.setText(LocaleController.formatYearMont(calendar.getTimeInMillis() / 1000, true));

            updateRowSelections(this, false);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(cellCount * (44 + 8) + 44), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            int currentCell = 0;
            int currentColumn = startDayOfWeek;
            float xStep = getMeasuredWidth() / 7f;
            float yStep = AndroidUtilities.dp(44 + 8);

            int selSize = AndroidUtilities.dp(44);
            for (int row = 0; row < Math.ceil((startDayOfWeek + daysInMonth) / 7f); row++) {
                float cy = yStep * row + yStep / 2f + AndroidUtilities.dp(44);
                RowAnimationValue v = rowSelectionPos.get(row);
                if (v != null) {
                    selectPaint.setColor(Theme.getColor(Theme.key_chat_messagePanelVoiceBackground));
                    selectPaint.setAlpha((int) (v.alpha * 0x66));
                    tempRect.set(v.startX - selSize / 2f, cy - selSize / 2f, v.endX + selSize / 2f, cy + selSize / 2f);
                    int dp = AndroidUtilities.dp(32);
                    canvas.drawRoundRect(tempRect, dp, dp, selectPaint);
                }
            }
            for (int i = 0; i < daysInMonth; i++) {
                float cx = xStep * currentColumn + xStep / 2f;
                float cy = yStep * currentCell + yStep / 2f + AndroidUtilities.dp(44);
                int nowTime = (int) (System.currentTimeMillis() / 1000L);

                PeriodDay day = messagesByDays != null ? messagesByDays.get(i, null) : null;
                if (nowTime < startMonthTime + (i + 1) * 86400 || startMonthTime + (i + 1) * 86400 < mMinDate) {
                    int oldAlpha = textPaint.getAlpha();
                    textPaint.setAlpha((int) (oldAlpha * 0.3f));
                    canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                    textPaint.setAlpha(oldAlpha);
                } else if (day != null && day.hasImage) {
                    float alpha = 1f;
                    if (imagesByDays.get(i) != null) {
                        if (checkEnterItems && !day.wasDrawn) {
                            day.enterAlpha = 0f;
                            day.startEnterDelay = (cy + getY()) / listView.getMeasuredHeight() * 150;
                        }
                        if (day.startEnterDelay > 0) {
                            day.startEnterDelay -= 16;
                            if (day.startEnterDelay < 0) {
                                day.startEnterDelay = 0;
                            } else {
                                invalidate();
                            }
                        }
                        if (day.startEnterDelay == 0 && day.enterAlpha != 1f) {
                            day.enterAlpha += 16 / 220f;
                            if (day.enterAlpha > 1f) {
                                day.enterAlpha = 1f;
                            } else {
                                invalidate();
                            }
                        }
                        alpha = day.enterAlpha;
                        if (alpha != 1f) {
                            canvas.save();
                            float s = 0.8f + 0.2f * alpha;
                            canvas.scale(s, s,cx, cy);
                        }
                        int pad = (int) (AndroidUtilities.dp(7f) * day.selectProgress);
                        if (day.selectStartEndProgress >= 0.01f) {
                            selectPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                            selectPaint.setAlpha((int) (day.selectStartEndProgress * 0xFF));
                            canvas.drawCircle(cx, cy, AndroidUtilities.dp(44) / 2f, selectPaint);

                            selectOutlinePaint.setColor(Theme.getColor(Theme.key_chat_messagePanelVoiceBackground));
                            tempRect.set(cx - AndroidUtilities.dp(44) / 2f, cy - AndroidUtilities.dp(44) / 2f, cx + AndroidUtilities.dp(44) / 2f, cy + AndroidUtilities.dp(44) / 2f);
                            canvas.drawArc(tempRect, -90, day.selectStartEndProgress * 360, false, selectOutlinePaint);
                        }

                        imagesByDays.get(i).setAlpha(day.enterAlpha);
                        imagesByDays.get(i).setImageCoords(cx - (AndroidUtilities.dp(44) - pad) / 2f, cy - (AndroidUtilities.dp(44) - pad) / 2f, AndroidUtilities.dp(44) - pad, AndroidUtilities.dp(44) - pad);
                        imagesByDays.get(i).draw(canvas);

                        blackoutPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (day.enterAlpha * 80)));
                        canvas.drawCircle(cx, cy, (AndroidUtilities.dp(44) - pad) / 2f, blackoutPaint);
                        day.wasDrawn = true;
                        if (alpha != 1f) {
                            canvas.restore();
                        }
                    }
                    if (alpha != 1f) {
                        int oldAlpha = textPaint.getAlpha();
                        textPaint.setAlpha((int) (oldAlpha * (1f - alpha)));
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                        textPaint.setAlpha(oldAlpha);

                        oldAlpha = textPaint.getAlpha();
                        activeTextPaint.setAlpha((int) (oldAlpha * alpha));
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), activeTextPaint);
                        activeTextPaint.setAlpha(oldAlpha);
                    } else {
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), activeTextPaint);
                    }
                } else {
                    if (day != null && day.selectStartEndProgress >= 0.01f) {
                        selectPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                        selectPaint.setAlpha((int) (day.selectStartEndProgress * 0xFF));
                        canvas.drawCircle(cx, cy, AndroidUtilities.dp(44) / 2f, selectPaint);

                        selectOutlinePaint.setColor(Theme.getColor(Theme.key_chat_messagePanelVoiceBackground));
                        tempRect.set(cx - AndroidUtilities.dp(44) / 2f, cy - AndroidUtilities.dp(44) / 2f, cx + AndroidUtilities.dp(44) / 2f, cy + AndroidUtilities.dp(44) / 2f);
                        canvas.drawArc(tempRect, -90, day.selectStartEndProgress * 360, false, selectOutlinePaint);

                        int pad = (int) (AndroidUtilities.dp(7f) * day.selectStartEndProgress);
                        selectPaint.setColor(Theme.getColor(Theme.key_chat_messagePanelVoiceBackground));
                        selectPaint.setAlpha((int) (day.selectStartEndProgress * 0xFF));
                        canvas.drawCircle(cx, cy, (AndroidUtilities.dp(44) - pad) / 2f, selectPaint);

                        float alpha = day.selectStartEndProgress;
                        if (alpha != 1f) {
                            int oldAlpha = textPaint.getAlpha();
                            textPaint.setAlpha((int) (oldAlpha * (1f - alpha)));
                            canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                            textPaint.setAlpha(oldAlpha);

                            oldAlpha = textPaint.getAlpha();
                            activeTextPaint.setAlpha((int) (oldAlpha * alpha));
                            canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), activeTextPaint);
                            activeTextPaint.setAlpha(oldAlpha);
                        } else {
                            canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), activeTextPaint);
                        }
                    } else {
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                    }
                }

                currentColumn++;
                if (currentColumn >= 7) {
                    currentColumn = 0;
                    currentCell++;
                }
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attached = true;
            if (imagesByDays != null) {
                for (int i = 0; i < imagesByDays.size(); i++) {
                    imagesByDays.valueAt(i).onAttachedToWindow();
                }
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            attached = false;
            if (imagesByDays != null) {
                for (int i = 0; i < imagesByDays.size(); i++) {
                    imagesByDays.valueAt(i).onDetachedFromWindow();
                }
            }
        }
    }

    private void animateSelection() {
        updateTitleAndButton();
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f).setDuration(300);
        a.setInterpolator(Easings.easeInOutQuad);
        a.addUpdateListener(animation -> {
            float selectProgress = (float) animation.getAnimatedValue();
            for (int j = 0; j < listView.getChildCount(); j++) {
                MonthView m = (MonthView) listView.getChildAt(j);
                m.setSelectionValue(selectProgress);
            }
        });
        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                for (int j = 0; j < listView.getChildCount(); j++) {
                    MonthView m = (MonthView) listView.getChildAt(j);
                    m.startSelectionAnimation(dateSelectedStart, dateSelectedEnd);
                }
            }
        });
        a.start();
        selectionAnimator = a;

        int minIndex = -1, maxIndex = -1;
        for (int j = 0; j < listView.getChildCount(); j++) {
            MonthView m = (MonthView) listView.getChildAt(j);
            int index = listView.getChildAdapterPosition(m);
            if (minIndex == -1)
                minIndex = index;
            maxIndex = index;
            updateRowSelections(m, true);
        }

        if (minIndex != -1) adapter.notifyItemRangeChanged(0, minIndex);
        if (maxIndex != -1) adapter.notifyItemRangeChanged(maxIndex, adapter.getItemCount() - maxIndex);
    }

    private void updateRowSelections(MonthView m, boolean animate) {
        if (dateSelectedStart == 0 || dateSelectedEnd == 0) {
            m.dismissRowAnimations(animate);
        } else {
            if (m.messagesByDays == null) return;

            int row = 0;
            int dayInRow = m.startDayOfWeek;
            int sDay = -1, eDay = -1;
            for (int i = 0; i < m.daysInMonth; i++) {
                PeriodDay day = m.messagesByDays.get(i, null);
                if (day != null) {
                    if (day.date >= dateSelectedStart && day.date <= dateSelectedEnd) {
                        if (sDay == -1)
                            sDay = dayInRow;
                        eDay = dayInRow;
                    }
                }

                dayInRow++;
                if (dayInRow >= 7) {
                    dayInRow = 0;
                    if (sDay != -1 && eDay != -1) {
                        m.animateRow(row, sDay, eDay, true, animate);
                    } else m.animateRow(row, 0, 0, false, animate);

                    row++;
                    sDay = -1;
                    eDay = -1;
                }
            }
            if (sDay != -1 && eDay != -1) {
                m.animateRow(row, sDay, eDay, true, animate);
            } else m.animateRow(row, 0, 0, false, animate);
        }
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public interface Callback {
        void onDateSelected(int date);
    }

    private static class PeriodDay {
        MessageObject messageObject;
        int startOffset;
        float enterAlpha = 1f;
        float startEnterDelay = 1f;
        boolean wasDrawn;

        int date;
        boolean hasImage = true;

        float selectStartEndProgress;
        float fromSelSEProgress;
        float toSelSEProgress;

        float selectProgress;
        float fromSelProgress;
        float toSelProgress;
    }

    @Override
    public boolean onBackPressed() {
        if (dateSelectedStart != 0 && dateSelectedEnd != 0) {
            dateSelectedStart = dateSelectedEnd = 0;
            animateSelection();
            return false;
        }
        return !actionsPreviewView.onBackPressed();
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ThemeDescription.ThemeDescriptionDelegate descriptionDelegate = this::updateColors;
        return new ArrayList<>(Arrays.asList(
            new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_windowBackgroundWhite),
            new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_windowBackgroundWhiteBlackText),
            new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_listSelector),
            new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_chat_messagePanelVoiceBackground)
        ));
    }

    @Override
    public boolean needDelayOpenAnimation() {
        return true;
    }

    @Override
    protected void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        super.onTransitionAnimationStart(isOpen, backward);
        isOpened = true;
    }

    private void showClearHistory(Context ctx, int startDate, int endDate) {
        int daysCount = endDate / 86400 - startDate / 86400;

        LinearLayout ll = new LinearLayout(ctx);
        ll.setOrientation(LinearLayout.VERTICAL);
        TextView messageTextView = new TextView(ctx);
        messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        messageTextView.setText(AndroidUtilities.replaceTags(daysCount == 1 ? LocaleController.getString("AreYouSureClearHistoryRangeOne", R.string.AreYouSureClearHistoryRangeOne) : LocaleController.formatString("AreYouSureClearHistoryRange", R.string.AreYouSureClearHistoryRange, LocaleController.getPluralString("SelectedDays", daysCount).replace("un1", String.valueOf(daysCount)))));
        ll.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 24, 0, 24, 6));

        CheckBoxCell cell = new CheckBoxCell(ctx, 1, themeDelegate);
        cell.setBackground(Theme.getSelectorDrawable(false));
        cell.setText(LocaleController.formatString("ClearHistoryOptionAlso", R.string.ClearHistoryOptionAlso, UserObject.getFirstName(getMessagesController().getUser(dialogId))), "", false, false);
        cell.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(5), 0, LocaleController.isRTL ? AndroidUtilities.dp(5) : AndroidUtilities.dp(16), 0);
        ll.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.LEFT));
        boolean[] deleteForAll = {false};
        cell.setOnClickListener(v -> {
            CheckBoxCell cell1 = (CheckBoxCell) v;
            deleteForAll[0] = !deleteForAll[0];
            cell1.setChecked(deleteForAll[0], true);
        });
        int finalStartDate = startDate;
        int finalEndDate = endDate;
        AlertDialog d = new AlertDialog.Builder(ctx, themeDelegate)
                .setTitle(LocaleController.getString("ClearHistoryRangeTitle", R.string.ClearHistoryRangeTitle))
                .setView(ll)
                .setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null)
                .setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialog, which) -> {
                            int wasStart = dateSelectedStart, wasEnd = dateSelectedEnd;
                            dateSelectedStart = dateSelectedEnd = 0;
                            animateSelection();

                            undoView.showWithAction(dialogId, UndoView.ACTION_CLEAR, () -> {
                                getMessagesController().deleteDialog(dialogId, 4, deleteForAll[0], finalStartDate, finalEndDate);
                                endReached = false;
                                messagesByYearMonth.clear();
                                loadNext();
                            }, () -> {
                                dateSelectedStart = wasStart;
                                dateSelectedEnd = wasEnd;
                                animateSelection();
                            });
                        })
                .create();
        showDialog(d);
        TextView button = (TextView) d.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (actionsPreviewView.getChatActivity() != null)
            actionsPreviewView.getChatActivity().onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (actionsPreviewView.getChatActivity() != null)
            actionsPreviewView.getChatActivity().onPause();
    }

    private void prepareBlurBitmap() {
        if (blurredView == null) {
            return;
        }
        View captureView = parentLayout;
        int w = (int) (captureView.getMeasuredWidth() / 6.0f);
        int h = (int) (captureView.getMeasuredHeight() / 6.0f);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(1.0f / 6.0f, 1.0f / 6.0f);
        captureView.draw(canvas);
        Utilities.stackBlurBitmap(bitmap, Math.max(7, Math.max(w, h) / 180));
        blurredView.setBackground(new BitmapDrawable(getParentActivity().getResources(), bitmap));
        blurredView.setAlpha(0.0f);
        blurredView.setVisibility(View.VISIBLE);
        actionsPreviewView.setBlurredView(blurredView);
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        if (dateSelectedStart != 0 || dateSelectedEnd != 0)
            return false;
        return actionsPreviewView == null || !actionsPreviewView.isVisible();
    }

    private final static class RowAnimationValue {
        float startX, endX;
        float alpha;

        RowAnimationValue(float s, float e, float a) {
            startX = s;
            endX = e;
            alpha = a;
        }
    }
}
