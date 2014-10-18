package sneer;

import java.util.Comparator;
import java.util.List;

import rx.Observable;
import sneer.commons.Comparators;
import sneer.rx.Observed;

public interface Conversation {

	Party party();
		
	Observable<List<Message>> messages();
	Observed<Long> mostRecentMessageTimestamp();
	Observed<String> mostRecentMessageContent();
	
	/** Publish a new message with isOwn() true, with party() as the audience and using System.currentTimeMillis() as the timestamp. */
	void sendText(String text);
	void sendMessage(String messageType, String text, String url, byte[] jpegImage, Object payload);

	Observable<List<ConversationMenuItem>> menu();
	
	Observable<Long> unreadMessageCount();
	void setBeingRead(boolean isBeingRead);
	
	Comparator<Conversation> MOST_RECENT_FIRST = new Comparator<Conversation>() {  @Override public int compare(Conversation i1, Conversation i2) {
		return Comparators.compare(i1.mostRecentMessageTimestamp().current(), i2.mostRecentMessageTimestamp().current());
	}};
	
}
