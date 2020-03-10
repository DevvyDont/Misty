package sh.niall.misty.utils.tags;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.types.ObjectId;
import sh.niall.yui.commands.Context;
import sh.niall.yui.exceptions.CommandException;

import java.time.Instant;

public class Tag {

    private MongoCollection<Document> db;
    public String friendlyName;
    public String searchName;
    public String body;
    public long author;
    public long guild;
    public long timestamp;
    public int uses;
    private Document originalDocument = null;


    /**
     * Creates a new tag
     *
     * @param db           The collection for tags
     * @param guild        The guild to look in
     * @param friendlyName The name of the tag
     * @param body         The tag text
     */
    public Tag(MongoCollection<Document> db, long guild, long author, String friendlyName, String body) throws CommandException {
        this.db = db;
        this.friendlyName = friendlyName;
        this.searchName = generateSearchName(friendlyName);
        this.body = body;
        this.author = author;
        this.guild = guild;
        this.timestamp = Instant.now().getEpochSecond();
        this.uses = 0;
    }

    /**
     * Loads a tag from the provided document
     *
     * @param db       The collection for tags
     * @param document The document to load from
     */
    public Tag(MongoCollection<Document> db, Document document) {
        this.db = db;
        loadFromDocument(document);
    }

    /**
     * Gets a Tag from the database
     *
     * @param db         The collection for tags
     * @param guild      The guild to look in
     * @param searchName The name to look for
     * @throws CommandException thrown when the tag can't be found
     */
    public Tag(MongoCollection<Document> db, long guild, String searchName) throws CommandException {
        Document document = db.find(Filters.and(Filters.eq("guild", guild), Filters.eq("searchName", searchName))).first();
        if (document == null)
            throw new CommandException(String.format("Tag `%s` not found!", searchName));

        this.db = db;
        loadFromDocument(document);
    }

    /**
     * Fills in the tag from the provided document
     *
     * @param document The document to fill from
     */
    private void loadFromDocument(Document document) {
        this.originalDocument = document;
        this.friendlyName = document.getString("friendlyName");
        this.searchName = document.getString("searchName");
        this.body = document.getString("body");
        this.author = document.getLong("author");
        this.guild = document.getLong("guild");
        this.timestamp = document.getLong("timestamp");
        this.uses = document.getInteger("uses");
    }

    /**
     * Updates the tag in the database or inserts a new tag into it
     *
     * @throws CommandException Thrown if a tag already exists when inserting a new tag
     */
    public Tag save() throws CommandException {
        Document document = new Document();

        if (originalDocument == null) {
            // We're trying to save a new tag

            // Check to see if a tag already exists
            if (db.find(Filters.and(Filters.eq("guild", this.guild), Filters.eq("searchName", this.searchName))).first() != null)
                throw new CommandException(String.format("Tag %s already exists!", this.friendlyName));

            // We're working with a fresh tag so insert it!
            document.append("friendlyName", this.friendlyName);
            document.append("searchName", this.searchName);
            document.append("body", this.body);
            document.append("author", this.author);
            document.append("guild", this.guild);
            document.append("timestamp", this.timestamp);
            document.append("uses", this.uses);
            db.insertOne(document);
        } else {
            ObjectId objectId = this.originalDocument.get("_id", ObjectId.class);

            if (!originalDocument.getString("friendlyName").equals(this.friendlyName))
                document.append("friendlyName", this.friendlyName);

            if (!originalDocument.getString("searchName").equals(this.searchName))
                document.append("searchName", this.searchName);

            if (!originalDocument.getString("body").equals(this.body))
                document.append("body", this.body);

            if (!originalDocument.getLong("author").equals(this.author))
                document.append("author", this.author);

            if (!originalDocument.getLong("guild").equals(this.guild))
                document.append("guild", this.guild);

            if (!originalDocument.getLong("timestamp").equals(this.timestamp))
                document.append("timestamp", this.timestamp);

            if (!originalDocument.getInteger("uses").equals(this.uses))
                document.append("uses", this.uses);

            db.updateOne(Filters.eq("_id", objectId), new Document("$set", document));
        }
        return this;
    }

    /**
     * Deletes the tag from the database
     */
    public Tag delete() {
        db.deleteOne(Filters.eq("_id", originalDocument.get("_id", ObjectId.class)));
        return this;
    }

    /**
     * Generates a search name from a string
     *
     * @param friendlyName The string to convert
     * @return The search name
     */
    public static String generateSearchName(String friendlyName) {
        return friendlyName.trim().toLowerCase();
    }

    /**
     * Validates a tag name
     *
     * @param searchName The name to validate
     * @throws CommandException Thrown if there's an issue with the name
     */
    public static void validateName(Context ctx, String searchName) throws CommandException {
        int totalCharacters = searchName.replace(" ", "").length();
        if (!(3 <= totalCharacters && totalCharacters <= 30))
            throw new CommandException("Playlist names must be between 3 and 30 characters!");

        if (!searchName.matches("^[a-zA-Z0-9 ]*$"))
            throw new CommandException("Playlist name must only contain alphanumeric characters (a-Z & 0-9)!");

        if (!searchName.equals(searchName.replaceAll(" +", " ")))
            throw new CommandException("Playlist names can't have multiple spaces!");

        int reservedWordIndex = ctx.getSubCommands().indexOf(searchName.split(" ")[0]);
        if (reservedWordIndex > -1)
            throw new CommandException(String.format("Tag name is using the reserved keyword `%s`!", ctx.getSubCommands().get(reservedWordIndex)));
    }

    /**
     * Validates a tag content
     *
     * @param body The content to validate
     * @throws CommandException Thrown if there's an issue with the name
     */
    public static void validateBody(String body) throws CommandException {
        int length = body.length();
        if (!(1 <= length && length <= 500))
            throw new CommandException("Tag content must be between 1 and 500 characters");
    }
}
