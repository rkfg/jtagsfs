package ru.rkfg.jtagsfs.domain;

import java.util.Set;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(indexes = { @Index(columnList = "name") })
public class Tag {
    @Id
    @GeneratedValue
    Long id;
    String name;
    @ManyToMany(mappedBy = "tags", fetch = FetchType.LAZY)
    Set<FileRecord> files;
    @ManyToOne
    Tag parent;

    public Tag() {
    }

    public Tag(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<FileRecord> getFiles() {
        return files;
    }

    public void setFiles(Set<FileRecord> files) {
        this.files = files;
    }

    public Tag getParent() {
        return parent;
    }

    public void setParent(Tag parent) {
        this.parent = parent;
    }

}
