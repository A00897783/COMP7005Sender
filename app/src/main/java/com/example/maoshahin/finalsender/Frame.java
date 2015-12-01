package com.example.maoshahin.finalsender;

/**
 * Created by mao on 15/11/30.
 */

import android.util.Base64;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class Frame implements Serializable {

    public String TYPE;
    public int SEQ;
    public byte[] DATA;

    public Frame (String t, int seq, byte[] data){
        TYPE = t;
        SEQ = seq;
        DATA = data;
    }

    public static Frame createFrameFromByte(byte[] arrived) {
        /*String encode = Base64.encodeToString(arrived, Base64.DEFAULT);
        ObjectMapper mapper = new ObjectMapper();
        Frame f = null;
        try {
            f = mapper.readValue(encode, Frame.class);
        } catch (IOException e) {
            e.printStackTrace();
        }*/
        Frame f = null;
        ByteArrayInputStream b = new ByteArrayInputStream(arrived);
        ObjectInputStream o = null;
        try {
            o = new ObjectInputStream(b);
            try {
                f = (Frame) o.readObject();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return f;
    }

    public byte[] toByte() {
        /*
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        String json = null;
        try {
            json = mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }*/
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        ObjectOutputStream o = null;
        try {
            o = new ObjectOutputStream(b);
            o.writeObject(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return b.toByteArray();
    }
}