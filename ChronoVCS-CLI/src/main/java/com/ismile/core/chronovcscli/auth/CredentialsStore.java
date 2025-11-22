package com.ismile.core.chronovcscli.auth;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CredentialsStore {
    private List<CredentialsEntry> servers = new ArrayList<>();
}
