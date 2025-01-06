package com.example.mapper;

import java.math.BigDecimal;
import java.util.Date;

public class LclPriceEntity {

    private int id;

    private String vlookup;

    private String product;

    private String pol;

    private String fc;

    private String destination;

    private String sort_type;

    private String speed_mode;

    private String region;

    private String currency;

    private Date effective_date;

    private BigDecimal export_clearance_per_bl;

    private BigDecimal import_clearance_per_bl;

    private BigDecimal less_than_5cbm_per_cbm;

    private BigDecimal between_5_10cbm_per_cbm;

    private BigDecimal between_10_15cbm_per_cbm;

    private BigDecimal greater_than_15cbm_per_cbm;

    private BigDecimal fx_usd_cny;

    private BigDecimal fx_eur_cny;

    private BigDecimal fx_gbp_cny;

    // 以下是各属性的Getter和Setter方法
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getVlookup() {
        return vlookup;
    }

    public void setVlookup(String vlookup) {
        this.vlookup = vlookup;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public String getPol() {
        return pol;
    }

    public void setPol(String pol) {
        this.pol = pol;
    }

    public String getFc() {
        return fc;
    }

    public void setFc(String fc) {
        this.fc = fc;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getSort_type() {
        return sort_type;
    }

    public void setSort_type(String sort_type) {
        this.sort_type = sort_type;
    }

    public String getSpeed_mode() {
        return speed_mode;
    }

    public void setSpeed_mode(String speed_mode) {
        this.speed_mode = speed_mode;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Date getEffective_date() {
        return effective_date;
    }

    public void setEffective_date(Date effective_date) {
        this.effective_date = effective_date;
    }

    public BigDecimal getExport_clearance_per_bl() {
        return export_clearance_per_bl;
    }

    public void setExport_clearance_per_bl(BigDecimal export_clearance_per_bl) {
        this.export_clearance_per_bl = export_clearance_per_bl;
    }

    public BigDecimal getImport_clearance_per_bl() {
        return import_clearance_per_bl;
    }

    public void setImport_clearance_per_bl(BigDecimal import_clearance_per_bl) {
        this.import_clearance_per_bl = import_clearance_per_bl;
    }

    public BigDecimal getLess_than_5cbm_per_cbm() {
        return less_than_5cbm_per_cbm;
    }

    public void setLess_than_5cbm_per_cbm(BigDecimal less_than_5cbm_per_cbm) {
        this.less_than_5cbm_per_cbm = less_than_5cbm_per_cbm;
    }

    public BigDecimal getBetween_5_10cbm_per_cbm() {
        return between_5_10cbm_per_cbm;
    }

    public void setBetween_5_10cbm_per_cbm(BigDecimal between_5_10cbm_per_cbm) {
        this.between_5_10cbm_per_cbm = between_5_10cbm_per_cbm;
    }

    public BigDecimal getBetween_10_15cbm_per_cbm() {
        return between_10_15cbm_per_cbm;
    }

    public void setBetween_10_15cbm_per_cbm(BigDecimal between_10_15cbm_per_cbm) {
        this.between_10_15cbm_per_cbm = between_10_15cbm_per_cbm;
    }

    public BigDecimal getGreater_than_15cbm_per_cbm() {
        return greater_than_15cbm_per_cbm;
    }

    public void setGreater_than_15cbm_per_cbm(BigDecimal greater_than_15cbm_per_cbm) {
        this.greater_than_15cbm_per_cbm = greater_than_15cbm_per_cbm;
    }

    public BigDecimal getFx_usd_cny() {
        return fx_usd_cny;
    }

    public void setFx_usd_cny(BigDecimal fx_usd_cny) {
        this.fx_usd_cny = fx_usd_cny;
    }

    public BigDecimal getFx_eur_cny() {
        return fx_eur_cny;
    }

    public void setFx_eur_cny(BigDecimal fx_eur_cny) {
        this.fx_eur_cny = fx_eur_cny;
    }

    public BigDecimal getFx_gbp_cny() {
        return fx_gbp_cny;
    }

    public void setFx_gbp_cny(BigDecimal fx_gbp_cny) {
        this.fx_gbp_cny = fx_gbp_cny;
    }
}