package com.example.maoshahin.finalsender;

/**
 * Created by mao on 15/11/30.
 */

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;

public class Frame {

    private String TYPE;
    private int SEQ;
    private byte[] DATA;

    public Frame (String t, int seq, byte[] data){
        TYPE = t;
        SEQ = seq;
        DATA = data;
    }
    public Frame (){

    }

    public void setTYPE(String t){
      TYPE = t;
    }
    public void setSEQ(int se){
        SEQ = se;
    }
    public void setDATA(byte[] a){
        DATA = a;
    }

    public String getTYPE(){
        return TYPE;
    }
    public int getSEQ(){
        return SEQ;
    }
    public byte[] getDATA(){
        return DATA;
    }

    /**
     * convert string to frame object using Jackson JSON
     * @param arrived data in string
     * @return frame with data arrived
     * @throws IOException
     */
    public static Frame createFrameFromString(String arrived) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Frame f = mapper.readValue(arrived, Frame.class);
        return f;
    }

    /**
     * change frame(this object) into string using Jackson JSON
     * @return
     */
    @Override
    public String toString() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        String json = null;
        try {
            json = mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return json;
    }
}