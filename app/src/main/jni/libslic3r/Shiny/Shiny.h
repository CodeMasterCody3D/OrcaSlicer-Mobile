// Stub for Shiny profiling library
#ifndef SHINY_SHINY_H_
#define SHINY_SHINY_H_

// All profiling macros are no-ops for mobile build
#define PROFILE_FUNC() ((void)0)
#define PROFILE_BLOCK(name) ((void)0)
#define PROFILE_CLEAR() ((void)0)
#define PROFILE_UPDATE() ((void)0)
#define PROFILE_OUTPUT(file) ((void)0)

#endif // SHINY_SHINY_H_
