package ru.rkfg.jtagsfs.domain;

import java.util.Set;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.OrderBy;

@Entity
public class FileRecord {
    @Id
    @GeneratedValue
    Long id;
    String name;
    @ManyToMany
    @OrderBy("name")
    Set<Tag> tags;

    public FileRecord() {
    }

    public FileRecord(String name, Set<Tag> tags) {
        super();
        this.name = name;
        this.tags = tags;
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

    public Set<Tag> getTags() {
        return tags;
    }

    public void setTags(Set<Tag> tags) {
        this.tags = tags;
    }

}
