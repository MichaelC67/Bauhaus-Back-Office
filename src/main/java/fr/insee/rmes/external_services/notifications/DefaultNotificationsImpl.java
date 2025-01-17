package fr.insee.rmes.external_services.notifications;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DefaultNotificationsImpl implements NotificationsContract {
	
	static final Logger logger = LogManager.getLogger(DefaultNotificationsImpl.class);
	
	public void notifyConceptCreation(String id, String uri) {
		logger.info("Notification : concept creation, id : {}, uri {}", id, uri);
	}
	
	public void notifyConceptUpdate(String id, String uri) {
		logger.info("Notification : concept update, id : {}, uri {}", id, uri);
	}
	
	public void notifyCollectionCreation(String id, String uri) {
		logger.info("Notification : collection creation, id : {}, uri {}", id, uri);
	}
	
	public void notifyCollectionUpdate(String id, String uri) {
		logger.info("Notification : collection update, id : {}, uri {}", id, uri);
	}

}
