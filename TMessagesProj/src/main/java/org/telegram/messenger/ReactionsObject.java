package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

public class ReactionsObject {
    public static String getAsString(TLRPC.TL_messageReactions reactions) {
        if (reactions == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(reactions.min);
        for (TLRPC.TL_reactionCount r : reactions.results) {
            sb.append(r.reaction).append(r.chosen).append(r.count);
        }
        return sb.toString();
    }
}
