package mgubran1.PayrollDev.trailers;

public class Trailer {
    private int id;
    private String trailerNumber;
    private String type;
    private Status status;
    private String notes;

    public enum Status { AVAILABLE, IN_USE, MAINTENANCE, RETIRED }

    public Trailer(int id, String trailerNumber, String type, Status status, String notes) {
        this.id = id;
        this.trailerNumber = trailerNumber;
        this.type = type;
        this.status = status;
        this.notes = notes;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getTrailerNumber() { return trailerNumber; }
    public void setTrailerNumber(String trailerNumber) { this.trailerNumber = trailerNumber; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
