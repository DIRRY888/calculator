package com.example.mapper;

import java.math.BigDecimal;
import java.util.Date;

public class FclPriceEntity {
    private int id;
    private String vlookup;
    private String product;
    private String pol;
    private String fc;
    private String destination;
    private String sort_type;
    private String speed_mode;
    private String dest_country;
    private String currency;
    private Date effective_date;
    private BigDecimal clearance;
    private BigDecimal freight;
    private BigDecimal fcl_price_per_ctn;
    private BigDecimal fx_usd_cny;
    private BigDecimal fx_eur_cny;
    private BigDecimal fx_gbp_cny;

    // Getter 方法
    public int getId() {
        return id;
    }

    public String getVlookup() {
        return vlookup;
    }

    public String getProduct() {
        return product;
    }

    public String getPol() {
        return pol;
    }

    public String getFc() {
        return fc;
    }

    public String getDestination() {
        return destination;
    }

    public String getSort_type() {
        return sort_type;
    }

    public String getSpeed_mode() {
        return speed_mode;
    }

    public String getDest_country() {
        return dest_country;
    }

    public String getCurrency() {
        return currency;
    }

    public Date getEffective_date() {
        return effective_date;
    }

    public BigDecimal getClearance() {
        return clearance;
    }

    public BigDecimal getFreight() {
        return freight;
    }

    public BigDecimal getFcl_price_per_ctn() {
        return fcl_price_per_ctn;
    }

    public BigDecimal getFx_usd_cny() {
        return fx_usd_cny;
    }

    public BigDecimal getFx_eur_cny() {
        return fx_eur_cny;
    }

    public BigDecimal getFx_gbp_cny() {
        return fx_gbp_cny;
    }

    // Setter 方法
    public void setId(int id) {
        this.id = id;
    }

    public void setVlookup(String vlookup) {
        this.vlookup = vlookup;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public void setPol(String pol) {
        this.pol = pol;
    }

    public void setFc(String fc) {
        this.fc = fc;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public void setSort_type(String sort_type) {
        this.sort_type = sort_type;
    }

    public void setSpeed_mode(String speed_mode) {
        this.speed_mode = speed_mode;
    }

    public void setDest_country(String dest_country) {
        this.dest_country = dest_country;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public void setEffective_date(Date effective_date) {
        this.effective_date = effective_date;
    }

    public void setClearance(BigDecimal clearance) {
        this.clearance = clearance;
    }

    public void setFreight(BigDecimal freight) {
        this.freight = freight;
    }

    public void setFcl_price_per_ctn(BigDecimal fcl_price_per_ctn) {
        this.fcl_price_per_ctn = fcl_price_per_ctn;
    }

    public void setFx_usd_cny(BigDecimal fx_usd_cny) {
        this.fx_usd_cny = fx_usd_cny;
    }

    public void setFx_eur_cny(BigDecimal fx_eur_cny) {
        this.fx_eur_cny = fx_eur_cny;
    }

    public void setFx_gbp_cny(BigDecimal fx_gbp_cny) {
        this.fx_gbp_cny = fx_gbp_cny;
    }
}