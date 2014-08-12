package io.intue.kamu.model;

/**
 * Created by ruwan on 11/8/14.
 */
public class Venue {

    private String name;
    private String id;
    private int distance;
    private String address;
    private String photoUrl;

    public Venue(String id, String name, int distance, String address, String photoUrl) {
        this.id = id;
        this.name = name;
        this.distance = distance;
        this.address = address;
        this.photoUrl = photoUrl;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public int getDistance() {
        return distance;
    }

    public String getId() {
        return id;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }
}
