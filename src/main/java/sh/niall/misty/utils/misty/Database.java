package sh.niall.misty.utils.misty;


import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.slf4j.LoggerFactory;
import sh.niall.misty.Misty;

public class Database {

    MongoClient mongoClient;
    MongoDatabase database;

    public Database() {
        LoggerFactory.getLogger(Database.class).info("Connecting to Mongo...");
        mongoClient = new MongoClient(new MongoClientURI(Misty.config.getMongoURL()));
        database = mongoClient.getDatabase("misty");
        LoggerFactory.getLogger(Database.class).info("Connect to mongo.");
    }

    public MongoCollection<Document> getCollection(String name) {
        return database.getCollection(name);
    }
}
