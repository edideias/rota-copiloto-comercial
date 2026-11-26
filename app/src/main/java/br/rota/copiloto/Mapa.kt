package br.rota.copiloto

import java.util.Locale

/** Mapa do bairro (OpenStreetMap + Leaflet): origem roxa, destino laranja e linha tracejada entre os dois. */
object Mapa {
    /** Mapa limpo: origem roxa, destino laranja e uma linha tracejada entre os dois. Usa o OpenStreetMap. */
    fun html(origem: Destino.Local?, l: Destino.Local): String {
        fun num(d: Double) = String.format(Locale.US, "%.5f", d)
        fun texto(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("<", "\\u003c") + "\""
        val head = "<!doctype html><html><head><meta charset=\"utf-8\"/>" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"/>" +
            "<link rel=\"stylesheet\" href=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.css\"/>" +
            "<script src=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.js\"></script>" +
            "<style>html,body,#m{height:100%;margin:0;background:#F5F3FF}" +
            ".leaflet-tooltip.rotulo{background:#fff;border:0;border-radius:10px;box-shadow:0 1px 6px rgba(17,24,39,.18);" +
            "font:600 12px sans-serif;color:#111827;padding:3px 8px}</style></head><body><div id=\"m\"></div><script>"
        val corpo = "var m=L.map('m',{zoomControl:false,attributionControl:false});" +
            "L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:18}).addTo(m);" +
            "var b=L.latLngBounds();" +
            "function pino(ll,cor,nome){L.circleMarker(ll,{radius:10,color:'#fff',weight:3,fillColor:cor,fillOpacity:1})" +
            ".addTo(m).bindTooltip(nome,{permanent:true,direction:'top',className:'rotulo'});b.extend(ll);}" +
            "var B=[" + num(l.lat) + "," + num(l.lon) + "];" +
            "pino(B," + texto("#F97316") + "," + texto(l.nome) + ");"
        val fim = if (origem != null) {
            "var A=[" + num(origem.lat) + "," + num(origem.lon) + "];" +
                "pino(A," + texto("#7C3AED") + "," + texto(origem.nome) + ");" +
                "L.polyline([A,B],{color:'#111827',weight:3,dashArray:'6 8'}).addTo(m);" +
                "m.fitBounds(b,{padding:[56,56]});"
        } else "m.setView(B,15);"
        return head + corpo + fim + "</script></body></html>"
    }

}
