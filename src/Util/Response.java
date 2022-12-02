package Util;

public class Response {

    private String status;
    private String code;
    private String header;
    private String body;
    private boolean inFile;
    private String file;

    private String error;

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public boolean isInFile() {
        return inFile;
    }

    public void setInFile(boolean inFile) {
        this.inFile = inFile;
    }

    @Override
    public String toString() {
        if(error != null){
            return error;
        }

        if(header == null){
            return body;
        }else{
            return header + body;
        }
    }

    public Response(String error) {
        this.error = error;
    }

    public Response(String status, String code, String header, String body) {
        this.status = status;
        this.code = code;
        this.header = header;
        this.body = body;
        this.error = null;
    }
}
