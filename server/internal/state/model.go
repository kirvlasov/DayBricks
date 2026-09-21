package state

import (
	"bytes"
	"encoding/json"
	"errors"
	"io"
	"regexp"
	"strings"
	"unicode/utf16"
)

const MaxBytes = 2 * 1024 * 1024

type Preset struct {
	ID              string  `json:"id"`
	Title           string  `json:"title"`
	Icon            *string `json:"icon,omitempty"`
	DurationMinutes *int    `json:"durationMinutes"`
	Description     string  `json:"description,omitempty"`
}

type Template struct {
	ID                     string   `json:"id"`
	Title                  string   `json:"title"`
	Icon                   *string  `json:"icon"`
	Description            string   `json:"description,omitempty"`
	DefaultDurationMinutes int      `json:"defaultDurationMinutes"`
	ReminderMinutes        *int     `json:"reminderMinutes"`
	SortOrder              int64    `json:"sortOrder"`
	Presets                []Preset `json:"presets"`
}

type Preferences struct {
	DominantHand string `json:"dominantHand"`
}

type State struct {
	SchemaVersion int         `json:"schemaVersion"`
	Templates     []Template  `json:"templates"`
	Preferences   Preferences `json:"preferences"`
}

func Empty() State {
	return State{SchemaVersion: 2, Templates: []Template{}, Preferences: Preferences{DominantHand: "RIGHT"}}
}

var uuid = regexp.MustCompile(`(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$`)

func textLength(value string) int { return len(utf16.Encode([]rune(value))) }
func validTitle(title string) bool {
	return strings.TrimSpace(title) != "" && textLength(title) <= 200
}

func (s State) Validate() error {
	invalid := errors.New("invalid state")
	if (s.SchemaVersion != 1 && s.SchemaVersion != 2) || s.Templates == nil || len(s.Templates) > 2000 {
		return invalid
	}
	if s.Preferences.DominantHand != "RIGHT" && s.Preferences.DominantHand != "LEFT" {
		return invalid
	}
	seen := map[string]bool{}
	for _, t := range s.Templates {
		if !uuid.MatchString(t.ID) || seen[t.ID] || !validTitle(t.Title) || t.DefaultDurationMinutes < 1 || t.DefaultDurationMinutes > 720 ||
			(t.ReminderMinutes != nil && (*t.ReminderMinutes < 0 || *t.ReminderMinutes > 10080)) || textLength(t.Description) > 2000 || len(t.Presets) > 100 || t.Presets == nil {
			return invalid
		}
		seen[t.ID] = true
		if t.Icon != nil && textLength(*t.Icon) > 32 {
			return invalid
		}
		presets := map[string]bool{}
		for _, p := range t.Presets {
			if !uuid.MatchString(p.ID) || presets[p.ID] || !validTitle(p.Title) || (p.Icon != nil && textLength(*p.Icon) > 32) ||
				(p.DurationMinutes != nil && (*p.DurationMinutes < 1 || *p.DurationMinutes > 720)) || textLength(p.Description) > 2000 {
				return invalid
			}
			presets[p.ID] = true
		}
	}
	return nil
}

func Decode(data []byte) (State, error) {
	var value State
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&value); err != nil {
		return value, err
	}
	if decoder.Decode(new(any)) != io.EOF {
		return value, errors.New("trailing JSON")
	}
	return value, value.Validate()
}
