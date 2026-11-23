package app.contouros.home;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Personal text stays in credential-encrypted storage; never used by the boot receiver. */
final class ConversationStore {
    static final int LIMIT = 60;
    static final class Message {
        final boolean owner; final String text;
        Message(boolean owner, String text) { this.owner = owner; this.text = text; }
    }
    private final SharedPreferences prefs;
    private final List<Message> messages = new ArrayList<>();
    ConversationStore(Context context) {
        prefs = context.getSharedPreferences("assistant_conversation_v1", Context.MODE_PRIVATE);
        try {
            JSONArray saved = new JSONArray(prefs.getString("messages", "[]"));
            for (int i = Math.max(0, saved.length() - LIMIT); i < saved.length(); i++) {
                JSONObject row = saved.getJSONObject(i);
                messages.add(new Message(row.getBoolean("owner"), row.getString("text")));
            }
        } catch (JSONException | ClassCastException ignored) { messages.clear(); }
    }
    List<Message> read() { return new ArrayList<>(messages); }
    void add(boolean owner, String text) {
        messages.add(new Message(owner, text));
        while (messages.size() > LIMIT) messages.remove(0);
        JSONArray result = new JSONArray();
        try {
            for (Message m : messages) result.put(new JSONObject().put("owner", m.owner).put("text", m.text));
            prefs.edit().putString("messages", result.toString()).apply();
        } catch (JSONException e) { throw new IllegalStateException(e); }
    }
}
