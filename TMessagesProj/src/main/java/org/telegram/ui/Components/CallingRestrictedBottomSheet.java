package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.MessagesController.findUpdatesAndRemove;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_phone;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;

public class CallingRestrictedBottomSheet extends BottomSheet {

    public CallingRestrictedBottomSheet(BaseFragment fragment, TLRPC.User user) {
        super(fragment.getParentActivity(), false);
        Context context = fragment.getParentActivity();
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        FrameLayout header = new FrameLayout(context);
        ImageView close = new ImageView(context);
        close.setImageResource(R.drawable.msg_close);
        close.setPadding(dp(6), dp(6), dp(6), dp(6));
        close.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), PorterDuff.Mode.SRC_IN);
        close.setBackground(Theme.AdaptiveRipple.filledCircle());
        close.setOnClickListener(v -> dismiss());
        header.addView(close, LayoutHelper.createFrame(40, 40, Gravity.END | Gravity.TOP, 0, 0, 6, 0));

        RLottieImageView imageView = new RLottieImageView(context);
        imageView.setAnimation(R.raw.shared_link_enter, 88, 88);
        imageView.playAnimation();
        FrameLayout bg = new FrameLayout(context);
        bg.setBackground(Theme.createCircleDrawable(AndroidUtilities.dp(88), Theme.getColor(Theme.key_featuredStickers_addButton)));
        bg.addView(imageView, LayoutHelper.createFrame(88, 88, Gravity.CENTER));
        header.addView(bg, LayoutHelper.createFrame(88, 88, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM));
        linearLayout.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 88 + 24));

        TextView title = new TextView(context);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());

        linearLayout.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 32, 16, 32, 0));

        TextView description = new TextView(context);
        description.setGravity(Gravity.CENTER);
        description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        description.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        linearLayout.addView(description, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 32, 12, 32, 16));

        TextView buttonTextView = new TextView(context);
        buttonTextView.setPadding(AndroidUtilities.dp(34), 0, AndroidUtilities.dp(34), 0);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        buttonTextView.setTypeface(AndroidUtilities.bold());
        buttonTextView.setBackground(Theme.AdaptiveRipple.filledRectByKey(Theme.key_featuredStickers_addButton, 8));
        buttonTextView.setText(LocaleController.getString(R.string.SendInviteLink));
        buttonTextView.setOnClickListener(v -> {
            AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
            progressDialog.showDelayed(350);

            TL_phone.createConferenceCall req = new TL_phone.createConferenceCall();
            req.random_id = Utilities.random.nextInt();
            fragment.getConnectionsManager().sendRequest(req, (response, error) -> {
                if (response instanceof TLRPC.Updates) {
                    TLRPC.Updates updates = (TLRPC.Updates) response;
                    MessagesController.getInstance(currentAccount).putUsers(updates.users, false);
                    MessagesController.getInstance(currentAccount).putChats(updates.chats, false);

                    TLRPC.GroupCall groupCall = null;
                    for (TLRPC.TL_updateGroupCall u : findUpdatesAndRemove(updates, TLRPC.TL_updateGroupCall.class)) {
                        groupCall = u.call;
                    }

                    if (groupCall == null) {
                        AndroidUtilities.runOnUIThread(progressDialog::dismiss);
                        return;
                    }

                    String link = groupCall.invite_link;
                    AndroidUtilities.runOnUIThread(()->{
                        SendMessagesHelper.SendMessageParams params = new SendMessagesHelper.SendMessageParams();
                        params.peer = user.id;
                        params.message = link;
                        fragment.getSendMessagesHelper().sendMessage(params);

                        if (!(fragment instanceof ChatActivity)) {
                            Bundle args = new Bundle();
                            args.putLong("user_id", user.id);
                            fragment.presentFragment(new INavigationLayout.NavigationParams(new ChatActivity(args)).setRemoveLast(true));
                        }

                        progressDialog.dismiss();
                        dismiss();
                    });
                } else {
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.needShowAlert, 6, error.text);
                    AndroidUtilities.runOnUIThread(progressDialog::dismiss);
                }
            });
        });

        FrameLayout buttonContainer = new FrameLayout(context);
        buttonContainer.addView(buttonTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_VERTICAL, 16, 0, 16, 0));
        buttonContainer.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
        linearLayout.addView(buttonContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 68, Gravity.BOTTOM));

        title.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.CallInviteViaLinkTitle)));
        description.setText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.CallingRestricted, ContactsController.formatName(user))));
        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(linearLayout);
        setCustomView(scrollView);
    }
}
