package org.agoncal.application.conference.speaker.data;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Antonio Goncalves
 *         http://www.antoniogoncalves.org
 *         --
 */
public class JSonToDatabase {

    /**
     * Data from http://cfp.devoxx.be/api/conferences/DV16/speakers
     *
     * create table sp_speaker (id varchar(255) not null, avatarUrl varchar(255), bio varchar(5000), blog varchar(255), company varchar(255), firstName varchar(255), language varchar(3), lastName varchar(255), twitter varchar(255), primary key (id))
     * create table sp_speaker_sp_talk (Speaker_id varchar(255) not null, acceptedTalks_id varchar(255) not null)
     * create table sp_talk (id varchar(255) not null, language varchar(255), title varchar(255), primary key (id))
     */

    private static List<String> acceptedTalkCreateSQLStatements;
    private static String speakerCreateSQLStatement;
    private static Map<String, String> talksAlreadyExist = new HashMap<>();

    public static void main(String[] args) throws IOException {

        File file = Paths.get("src/test/resources/speakers.json").toFile();
        JsonReader rdr = Json.createReader(new FileReader(file.getAbsoluteFile()));

        JsonArray results = rdr.readArray();
        for (JsonObject result : results.getValuesAs(JsonObject.class)) {
            acceptedTalkCreateSQLStatements = new ArrayList<>();
            speakerCreateSQLStatement = "INSERT INTO SP_Speaker (id, firstName, lastName, company, twitter, avatarUrl, language, blog, bio) values (";

            speakerCreateSQLStatement += getFormattedValue(result, "uuid") + ", ";
            speakerCreateSQLStatement += getFormattedValue(result, "firstName") + ", ";
            speakerCreateSQLStatement += getFormattedValue(result, "lastName") + ", ";
            speakerCreateSQLStatement += getFormattedValue(result, "company") + ", ";
            speakerCreateSQLStatement += getFormattedValue(result, "twitter") + ", ";
            speakerCreateSQLStatement += getValue(result, "avatarURL") + ", ";

            JsonArray links = result.getJsonArray("links");
            for (JsonObject link : links.getValuesAs(JsonObject.class)) {
                getSpeaker(result.getString("uuid"), link.getString("href"));
            }

            speakerCreateSQLStatement += ");";
            System.out.println(speakerCreateSQLStatement);
            for (String acceptedTalkCreateSQLStatement : acceptedTalkCreateSQLStatements) {
                System.out.println(acceptedTalkCreateSQLStatement);
            }
            System.out.println("");
        }
    }

    private static void getSpeaker(String speakerUUID, String hrefSpeaker) throws IOException {
        URL url = new URL(hrefSpeaker);
        try (InputStream is = url.openStream(); JsonReader rdr = Json.createReader(is)) {

            JsonObject result = rdr.readObject();
            speakerCreateSQLStatement += getFormattedValue(result, "lang") + ", ";
            speakerCreateSQLStatement += getValue(result, "blog") + ", ";
            speakerCreateSQLStatement += getFormattedValue(result, "bio");

            JsonArray acceptedTalks = result.getJsonArray("acceptedTalks");
            for (int i = 0; i < acceptedTalks.size(); i++) {
                JsonArray links = acceptedTalks.getJsonObject(i).getJsonArray("links");

                for (JsonObject link : links.getValuesAs(JsonObject.class)) {
                    String hrefTalk = link.getString("href");
                    if (hrefTalk.contains("/talks"))
                        acceptedTalkCreateSQLStatements.addAll(getTalk(speakerUUID, hrefTalk));
                }
            }
        }
    }

    private static List<String> getTalk(String speakerUUID, String hrefTalk) throws IOException {
        List<String> sqlStatements = new ArrayList<>();
        URL url = new URL(hrefTalk);
        String acceptedTalkCreateSQLStatement;
        String joinTableCreateSQLStatement;
        try (InputStream is = url.openStream(); JsonReader rdr = Json.createReader(is)) {
            JsonObject result = rdr.readObject();

            if (!talksAlreadyExist.containsKey(result.getString("id"))) {
                talksAlreadyExist.put(result.getString("id"), "exists");
                acceptedTalkCreateSQLStatement = "INSERT INTO SP_Talk (id, title, language) values (";
                acceptedTalkCreateSQLStatement += "'" + result.getString("id") + "', ";
                acceptedTalkCreateSQLStatement += getFormattedValue(result, "title") + ", ";
                acceptedTalkCreateSQLStatement += getFormattedValue(result, "lang");
                acceptedTalkCreateSQLStatement += ");";
                sqlStatements.add(acceptedTalkCreateSQLStatement);
            }

            joinTableCreateSQLStatement = "INSERT INTO SP_Speaker_SP_Talk (Speaker_id, acceptedTalks_id) values (";
            joinTableCreateSQLStatement += "'" + speakerUUID + "', ";
            joinTableCreateSQLStatement += "'" + result.getString("id") + "', ";
            joinTableCreateSQLStatement += ");";
        }
        sqlStatements.add(joinTableCreateSQLStatement);
        return sqlStatements;
    }

    private static String getFormattedValue(JsonObject jsonObject, String key) {
        try {
            String value = jsonObject.getString(key);
            if (value == null) {
                return "null";
            } else {
                return "'" + value.replaceAll("[\\t\\r\\n\\-\\+\\.\\^:,'“”]", " ").trim() + "'";
            }
        } catch (Exception e) {
            return "null";
        }
    }

    private static String getValue(JsonObject jsonObject, String key) {
        try {
            String value = jsonObject.getString(key);
            if (value == null) {
                return "null";
            } else {
                return "'" + value.trim() + "'";
            }
        } catch (Exception e) {
            return "null";
        }
    }
}
