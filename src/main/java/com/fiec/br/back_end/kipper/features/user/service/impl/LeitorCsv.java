package com.fiec.br.back_end.kipper.features.user.service.impl;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Leitor de CSV simples (RFC 4180), sem dependência externa.
 * <ul>
 *   <li>Separador "," ou ";" (o Excel em português salva com ";"), detectado pelo cabeçalho;</li>
 *   <li>campos entre aspas, com aspas duplicadas ("") e quebras de linha dentro das aspas;</li>
 *   <li>UTF-8 (com ou sem BOM); se o arquivo não for UTF-8 válido, lê como Windows-1252,
 *       que é como o Excel no Windows costuma salvar.</li>
 * </ul>
 */
final class LeitorCsv {

    /** Uma linha lida: o número dela no arquivo (começando em 1) e os campos. */
    record Linha(int numero, List<String> campos) {}

    private LeitorCsv() {}

    static String decodificar(byte[] bytes) {
        try {
            String texto = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return texto.startsWith("﻿") ? texto.substring(1) : texto;
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("windows-1252"));
        }
    }

    /** Lê todas as linhas, ignorando as que estão totalmente em branco. */
    static List<Linha> ler(String texto) {
        char separador = detectarSeparador(texto);
        List<Linha> linhas = new ArrayList<>();
        List<String> campos = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean entreAspas = false;
        int linhaAtual = 1;
        int inicioDoRegistro = 1;

        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (entreAspas) {
                if (c == '"') {
                    if (i + 1 < texto.length() && texto.charAt(i + 1) == '"') {
                        campo.append('"');
                        i++;
                    } else {
                        entreAspas = false;
                    }
                } else {
                    if (c == '\n') linhaAtual++;
                    campo.append(c);
                }
            } else if (c == '"' && campo.isEmpty()) {
                entreAspas = true;
            } else if (c == separador) {
                campos.add(campo.toString());
                campo.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < texto.length() && texto.charAt(i + 1) == '\n') i++;
                campos.add(campo.toString());
                campo.setLength(0);
                adicionarSeNaoVazia(linhas, inicioDoRegistro, campos);
                campos = new ArrayList<>();
                linhaAtual++;
                inicioDoRegistro = linhaAtual;
            } else {
                campo.append(c);
            }
        }
        if (entreAspas) {
            throw new IllegalArgumentException("CSV inválido: aspas abertas e não fechadas a partir da linha "
                    + inicioDoRegistro + ".");
        }
        campos.add(campo.toString());
        adicionarSeNaoVazia(linhas, inicioDoRegistro, campos);
        return linhas;
    }

    private static void adicionarSeNaoVazia(List<Linha> linhas, int numero, List<String> campos) {
        boolean vazia = campos.stream().allMatch(String::isBlank);
        if (!vazia) linhas.add(new Linha(numero, campos));
    }

    /** Usa o separador que mais aparece na primeira linha (fora de aspas). */
    private static char detectarSeparador(String texto) {
        int virgulas = 0, pontoEVirgulas = 0;
        boolean entreAspas = false;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == '"') entreAspas = !entreAspas;
            else if (!entreAspas && (c == '\n' || c == '\r')) break;
            else if (!entreAspas && c == ',') virgulas++;
            else if (!entreAspas && c == ';') pontoEVirgulas++;
        }
        return pontoEVirgulas > virgulas ? ';' : ',';
    }
}
